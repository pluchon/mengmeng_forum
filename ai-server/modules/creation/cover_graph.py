"""帖子封面理解、按需检索、提示词与生图 LangGraph 子图。"""

from __future__ import annotations

from typing import Annotated, Any
import logging
import operator
import re

from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel
from typing_extensions import TypedDict

from clients.dashscope_chat_client import json_chat_completion, lc_messages_to_openai
from clients.llm import flash_model_name
from mcp.registry import invoke_tool
from modules.creation.article_understanding import ArticleUnderstanding, analyze_article
from modules.creation.service import generate_image
from modules.creation.usage import aggregate_usage, usage_item

logger = logging.getLogger(__name__)


class CoverPrompt(BaseModel):
    prompt: str


class CoverState(TypedDict, total=False):
    title: str
    content: str
    editor_mode: str
    user_prompt: str
    quality: str
    understanding: ArticleUnderstanding
    search_context: str
    mcp_used: bool
    prompt: str
    url: str
    model: str
    deep_used: bool
    usages: Annotated[list[dict[str, Any]], operator.add]


def run_cover_graph(
    title: str,
    content: str,
    editor_mode: str,
    user_prompt: str,
    quality: str,
) -> dict[str, Any]:
    state = _COVER_GRAPH.invoke({
        "title": title,
        "content": content,
        "editor_mode": editor_mode,
        "user_prompt": user_prompt,
        "quality": quality,
        "mcp_used": False,
        "usages": [],
    })
    url = str(state.get("url") or "").strip()
    if not url:
        raise ValueError("AI 未返回封面图片")
    return {
        "url": url,
        "prompt": state.get("prompt", ""),
        "model": state.get("model", ""),
        "mcpUsed": bool(state.get("mcp_used")),
        "deepUsed": bool(state.get("deep_used")),
        "usage": aggregate_usage(state.get("usages") or []),
    }


def node_analyze_cover(state: CoverState) -> dict[str, Any]:
    understanding, usages, deep_used = analyze_article(
        state.get("title", ""),
        state.get("content", ""),
        state.get("editor_mode", "rich"),
    )
    return {"understanding": understanding, "deep_used": deep_used, "usages": usages}


def route_after_cover_analysis(state: CoverState) -> str:
    understanding = state["understanding"]
    return "search" if understanding.needs_search else "compose"


def node_search_cover_context(state: CoverState) -> dict[str, Any]:
    query = state["understanding"].search_query.strip()
    if not query:
        return {"search_context": "", "mcp_used": False}
    try:
        context = invoke_tool("tavily_search", {
            "query": query,
            "max_results": 5,
            "search_depth": "basic",
        })
        if not context or context.startswith("错误") or context.startswith("搜索失败"):
            return {"search_context": "", "mcp_used": False}
        return {"search_context": context[:2000], "mcp_used": True}
    except Exception:
        logger.exception("封面 Tavily 检索失败，继续使用正文生成")
        return {"search_context": "", "mcp_used": False}


def node_compose_cover_prompt(state: CoverState) -> dict[str, Any]:
    understanding = state["understanding"]
    quality = state.get("quality", "normal")
    model_family = "wan"
    system_prompt = _cover_prompt_system(model_family)
    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        (
            "human",
            "文章理解：{understanding}\n用户补充：{user_prompt}\n搜索参考：{search_context}\n"
            "只输出JSON，字段只有prompt。",
        ),
    ])
    try:
        messages = prompt.format_messages(
            understanding=understanding.model_dump_json(),
            user_prompt=state.get("user_prompt", "")[:200],
            search_context=state.get("search_context", "")[:2000],
        )
        raw, usage = _json_completion(messages, temperature=0.2)
        cover_prompt = CoverPrompt.model_validate_json(raw).prompt.strip()
        normalized = _normalize_cover_prompt(cover_prompt)
        if not normalized:
            raise ValueError("封面提示词为空")
        return {"prompt": normalized, "usages": [usage_item(usage, "cover_prompt")]}
    except Exception:
        logger.exception("封面提示词生成失败，使用结构化理解兜底")
        fallback = _fallback_cover_prompt(understanding, model_family)
        return {"prompt": _normalize_cover_prompt(fallback)}


def node_generate_cover(state: CoverState) -> dict[str, Any]:
    url, usage, _ = generate_image(state.get("prompt", ""), state.get("quality", "normal"), enrich=False)
    normalized_usage = usage_item(usage, "cover_image")
    return {
        "url": url,
        "model": normalized_usage["model_code"],
        "usages": [normalized_usage],
    }


def _json_completion(messages: list[Any], *, temperature: float) -> tuple[str, dict[str, Any]]:
    return json_chat_completion(
        flash_model_name(),
        lc_messages_to_openai(messages),
        temperature=temperature,
        timeout=180,
    )


def _cover_prompt_system(model_family: str) -> str:
    return (
        "你是文章封面提示词节点。必须忠于文章理解和用户补充，只设计一个明确主视觉。"
        "不得复述文章、使用抽象口号、堆砌同义形容词或给出多套方案。"
        "封面不需要任何文字、字幕、水印或logo。"
        "目标模型是阿里云万相 Wan 2.7。用流畅、紧凑的中文正向提示词，按"
        "主体及外观动作、具体场景、艺术风格、景别和视角、光线与氛围、关键细节的顺序描述。"
        "避免否定词列表，控制在约160个汉字内。"
    )


def _fallback_cover_prompt(understanding: ArticleUnderstanding, model_family: str) -> str:
    subject = understanding.visual_subject or understanding.summary[:80] or "文章主题"
    scene = understanding.visual_scene or "与主题直接相关的简洁场景"
    return f"{subject}，{scene}，现代清新插画，横向中景，主体居中，柔和自然光，{understanding.tone}氛围，无文字，无水印，无logo"


def _normalize_cover_prompt(prompt: str) -> str:
    text = re.sub(r"\s+", " ", str(prompt or "")).strip(" ，。\n")
    if not text:
        return ""
    if not re.search(r"无文字|不要文字|不含文字", text):
        text += "，无文字"
    if not re.search(r"无水印|不要水印|不含水印", text):
        text += "，无水印"
    return text[:800]


_builder = StateGraph(CoverState)
_builder.add_node("analyze", node_analyze_cover)
_builder.add_node("search", node_search_cover_context)
_builder.add_node("compose", node_compose_cover_prompt)
_builder.add_node("generate", node_generate_cover)
_builder.add_edge(START, "analyze")
_builder.add_conditional_edges("analyze", route_after_cover_analysis, {"search": "search", "compose": "compose"})
_builder.add_edge("search", "compose")
_builder.add_edge("compose", "generate")
_builder.add_edge("generate", END)
_COVER_GRAPH = _builder.compile()
