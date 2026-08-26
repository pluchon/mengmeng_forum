"""帖子创作子图共享的文章理解节点。"""

from __future__ import annotations

import logging
import re
import hashlib
import threading
import time
from collections import OrderedDict
from typing import Any

from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field
from typing_extensions import TypedDict

from clients.dashscope_chat_client import json_chat_completion, lc_messages_to_openai
from clients.llm import deep_model_name, flash_model_name
from modules.creation.usage import usage_item
from modules.creation.ranking import unique_clip

logger = logging.getLogger(__name__)
_CACHE_TTL_SECONDS = 600
_CACHE_MAX_ENTRIES = 128
_CACHE_LOCK = threading.Lock()
_CACHE: OrderedDict[str, tuple[float, "ArticleUnderstanding", bool]] = OrderedDict()


class ArticleUnderstanding(BaseModel):
    """标签与封面子图共用的结构化文章理解结果。"""

    summary: str = Field(min_length=1, max_length=240)
    topics: list[str] = Field(default_factory=list, max_length=8)
    key_entities: list[str] = Field(default_factory=list, max_length=8)
    visual_subject: str = Field(default="", max_length=120)
    visual_scene: str = Field(default="", max_length=160)
    tone: str = Field(default="自然", max_length=60)
    confidence: float = Field(default=0.6, ge=0, le=1)
    needs_deep: bool = False
    needs_search: bool = False
    unknown_terms: list[str] = Field(default_factory=list, max_length=5)
    search_query: str = Field(default="", max_length=120)


class ArticleUnderstandingState(TypedDict, total=False):
    title: str
    content: str
    editor_mode: str
    understanding: ArticleUnderstanding
    deep_used: bool
    usages: list[dict[str, Any]]


def analyze_article(
    title: str,
    content: str,
    editor_mode: str,
) -> tuple[ArticleUnderstanding, list[dict[str, Any]], bool]:
    """优先使用 Flash；仅极低置信度且模型明确请求时升级一次深度模型。"""

    cache_key = _cache_key(title, content, editor_mode)
    cached = _cache_get(cache_key)
    if cached is not None:
        understanding, deep_used = cached
        return understanding, [], deep_used
    state = _ARTICLE_UNDERSTANDING_GRAPH.invoke({
        "title": title,
        "content": content,
        "editor_mode": editor_mode,
        "usages": [],
    })
    understanding = state["understanding"]
    deep_used = bool(state.get("deep_used"))
    _cache_put(cache_key, understanding, deep_used)
    return understanding, state.get("usages") or [], deep_used


def node_analyze_article(state: ArticleUnderstandingState) -> dict[str, Any]:
    """执行共享文章理解子图中的模型节点。"""

    title = state.get("title", "")
    content = state.get("content", "")
    editor_mode = state.get("editor_mode", "rich")
    article = plain_article(content, editor_mode)[:16000]
    fallback = _fallback_understanding(title, article)
    if not article and not title.strip():
        return {"understanding": fallback, "usages": [], "deep_used": False}

    usages: list[dict[str, Any]] = []
    try:
        flash, usage = _invoke_understanding(title, article, _flash_model())
        usages.append(usage_item(usage, "article_understanding_flash"))
    except Exception:
        logger.exception("Flash 文章理解失败，使用本地兜底")
        return {"understanding": fallback, "usages": usages, "deep_used": False}

    if flash.needs_deep and flash.confidence < 0.4:
        try:
            deep, usage = _invoke_understanding(title, article, _deep_model())
            usages.append(usage_item(usage, "article_understanding_deep"))
            return {"understanding": deep, "usages": usages, "deep_used": True}
        except Exception:
            logger.exception("深度文章理解失败，继续采用 Flash 结果")
    return {"understanding": flash, "usages": usages, "deep_used": False}


def plain_article(content: str, editor_mode: str) -> str:
    """把富文本或 Markdown 正文转换成适合语义理解的纯文本。"""

    text = str(content or "")
    if editor_mode == "rich":
        text = re.sub(r"<script\b[^>]*>[\s\S]*?</script>", " ", text, flags=re.IGNORECASE)
        text = re.sub(r"<style\b[^>]*>[\s\S]*?</style>", " ", text, flags=re.IGNORECASE)
        text = re.sub(r"<[^>]+>", " ", text)
    else:
        text = re.sub(r"```[\s\S]*?```", " ", text)
        text = re.sub(r"!\[[^\]]*]\([^)]*\)", " ", text)
        text = re.sub(r"\[([^\]]+)]\([^)]*\)", r"\1", text)
    return re.sub(r"\s+", " ", text).strip()


def _invoke_understanding(
    title: str,
    article: str,
    model: str,
) -> tuple[ArticleUnderstanding, dict[str, Any]]:
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "你是帖子创作工作流的文章理解节点。只提取原文已经表达的内容，不新增事实。"
            "summary用自然中文概括核心内容；topics是可用于匹配论坛标签的主题词；"
            "visual_subject和visual_scene只描述可直接画出来的单一主视觉。"
            "只有内容专业、歧义明显且当前模型确实无法把握时才令needs_deep=true。"
            "只有陌生专有名词会直接影响画面外观时才令needs_search=true。"
            "必须只输出合法JSON对象。",
        ),
        (
            "human",
            "标题：{title}\n正文：{article}\n"
            "输出字段：summary、topics、key_entities、visual_subject、visual_scene、tone、"
            "confidence、needs_deep、needs_search、unknown_terms、search_query。",
        ),
    ])
    messages = prompt.format_messages(title=title[:200], article=article)
    raw, usage = json_chat_completion(
        model,
        lc_messages_to_openai(messages),
        temperature=0.1,
        timeout=180,
    )
    result = ArticleUnderstanding.model_validate_json(raw)
    normalized_topics = unique_clip(result.topics, limit=8, max_len=32)
    normalized_entities = unique_clip(result.key_entities, limit=8, max_len=40)
    normalized_terms = unique_clip(result.unknown_terms, limit=5, max_len=40)
    search_query = result.search_query.strip() if result.needs_search else ""
    return result.model_copy(update={
        "topics": normalized_topics,
        "key_entities": normalized_entities,
        "unknown_terms": normalized_terms,
        "needs_search": bool(result.needs_search and search_query),
        "search_query": search_query,
    }), usage


def _fallback_understanding(title: str, article: str) -> ArticleUnderstanding:
    summary = (article[:180] or title.strip() or "帖子内容").strip()
    visual_subject = title.strip() or summary[:80]
    return ArticleUnderstanding(
        summary=summary,
        topics=[],
        key_entities=[],
        visual_subject=visual_subject[:120],
        visual_scene="与文章主题直接相关的简洁场景",
        tone="自然",
        confidence=0.25,
    )


def _flash_model() -> str:
    return flash_model_name()


def _deep_model() -> str:
    return deep_model_name()


def _cache_key(title: str, content: str, editor_mode: str) -> str:
    raw = f"article-understanding-v1\n{editor_mode}\n{title}\n{content}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _cache_get(cache_key: str) -> tuple[ArticleUnderstanding, bool] | None:
    now = time.monotonic()
    with _CACHE_LOCK:
        cached = _CACHE.get(cache_key)
        if cached is None:
            return None
        expires_at, understanding, deep_used = cached
        if expires_at <= now:
            _CACHE.pop(cache_key, None)
            return None
        _CACHE.move_to_end(cache_key)
        return understanding.model_copy(deep=True), deep_used


def _cache_put(cache_key: str, understanding: ArticleUnderstanding, deep_used: bool) -> None:
    with _CACHE_LOCK:
        _CACHE[cache_key] = (
            time.monotonic() + _CACHE_TTL_SECONDS,
            understanding.model_copy(deep=True),
            deep_used,
        )
        _CACHE.move_to_end(cache_key)
        while len(_CACHE) > _CACHE_MAX_ENTRIES:
            _CACHE.popitem(last=False)


_builder = StateGraph(ArticleUnderstandingState)
_builder.add_node("analyze_article", node_analyze_article)
_builder.add_edge(START, "analyze_article")
_builder.add_edge("analyze_article", END)
_ARTICLE_UNDERSTANDING_GRAPH = _builder.compile()
