"""帖子配乐推荐与 AI 搜索选曲 LangGraph。"""

from __future__ import annotations

import json
import logging
import operator
from typing import Annotated, Any, Literal

from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field
from typing_extensions import TypedDict

from clients.dashscope_chat_client import json_chat_completion, lc_messages_to_openai
from clients.llm import flash_model_name
from modules.creation.article_understanding import ArticleUnderstanding, analyze_article
from modules.creation.music_graph_parse import parse_music_selection_payload, parse_search_intent_payload
from modules.creation.ranking import filter_ranked, unique_clip
from modules.creation.usage import aggregate_usage, usage_item

logger = logging.getLogger(__name__)

RecommendMode = Literal["recommend", "prefilter"]


class RankedMusic(BaseModel):
    musicKey: str = Field(min_length=1, max_length=128)
    score: float = Field(ge=0, le=1)
    reason: str = Field(default="", max_length=80)


class MusicSelection(BaseModel):
    tracks: list[RankedMusic] = Field(default_factory=list, max_length=12)


class SearchIntent(BaseModel):
    moods: list[str] = Field(default_factory=list, max_length=8)
    genre: str = Field(default="", max_length=40)
    artist: str = Field(default="", max_length=80)
    keywords: list[str] = Field(default_factory=list, max_length=8)
    summary: str = Field(default="", max_length=200)


class MusicState(TypedDict, total=False):
    title: str
    content: str
    editor_mode: str
    query: str
    scope: str
    candidates: list[dict[str, Any]]
    mode: RecommendMode
    understanding: ArticleUnderstanding
    intent: SearchIntent
    ranked: list[RankedMusic]
    music_keys: list[str]
    rationale: str
    moods: list[str]
    deep_used: bool
    usages: Annotated[list[dict[str, Any]], operator.add]


def run_music_recommend_graph(
    title: str,
    content: str,
    editor_mode: str,
    candidates: list[dict[str, Any]],
    mode: RecommendMode = "recommend",
) -> dict[str, Any]:
    state = _RECOMMEND_GRAPH.invoke({
        "title": title,
        "content": content,
        "editor_mode": editor_mode,
        "candidates": _normalize_candidates(candidates),
        "mode": mode,
        "usages": [],
    })
    understanding = state.get("understanding")
    moods = state.get("moods") or []
    if understanding and not moods:
        moods = _topics_to_moods(understanding.topics)
    return {
        "musicKeys": state.get("music_keys") or [],
        "rationale": state.get("rationale") or "",
        "moods": moods,
        "usage": aggregate_usage(state.get("usages") or []),
    }


def run_music_search_graph(
    query: str,
    candidates: list[dict[str, Any]],
    scope: str = "all",
) -> dict[str, Any]:
    state = _SEARCH_GRAPH.invoke({
        "query": query.strip(),
        "scope": _normalize_search_scope(scope),
        "candidates": _normalize_candidates(candidates),
        "usages": [],
    })
    intent = state.get("intent")
    moods = state.get("moods") or (intent.moods if intent else [])
    return {
        "musicKeys": state.get("music_keys") or [],
        "rationale": state.get("rationale") or "",
        "moods": moods,
        "usage": aggregate_usage(state.get("usages") or []),
    }


def node_understand_article(state: MusicState) -> dict[str, Any]:
    result, usages, deep_used = analyze_article(
        state.get("title", ""),
        state.get("content", ""),
        state.get("editor_mode", "rich"),
    )
    moods = _topics_to_moods(result.topics)
    return {"understanding": result, "moods": moods, "deep_used": deep_used, "usages": usages}


def node_select_recommend(state: MusicState) -> dict[str, Any]:
    candidates = state.get("candidates") or []
    if not candidates:
        return {"ranked": [], "rationale": "候选曲库为空"}
    understanding = state["understanding"]
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "你是论坛帖子配乐推荐节点。只能从候选歌曲中选择，禁止创造新 musicKey。"
            "选择标准是歌曲氛围、主题与帖子内容直接匹配，而不是仅有弱关联。"
            "可以返回 0 个；宁缺毋滥，通常 3-8 首，最多 12 首。"
            "score 表示匹配程度，取值 0 到 1 的小数，不要用百分制。"
            "只输出合法 JSON。",
        ),
        (
            "human",
            "帖子理解：{understanding}\n候选歌曲：{candidates}\n"
            "输出字段 tracks（musicKey、score、reason）与 rationale（一句话说明）。",
        ),
    ])
    try:
        raw, usage = _structured_completion(prompt, {
            "understanding": understanding.model_dump_json(),
            "candidates": json.dumps(candidates, ensure_ascii=False),
        }, temperature=0.05)
        payload = json.loads(raw)
        selected = MusicSelection.model_validate(parse_music_selection_payload(payload)).tracks
        ranked = _validate_ranked(selected, candidates, threshold=0.62, limit=12)
        rationale = str(payload.get("rationale") or "").strip()[:240]
        return {
            "ranked": ranked,
            "rationale": rationale,
            "usages": [usage_item(usage, "music_recommend_select")],
        }
    except Exception:
        logger.exception("配乐推荐候选选择失败")
        return {"ranked": [], "rationale": ""}


def route_after_recommend_selection(state: MusicState) -> str:
    return "rerank" if len(state.get("ranked") or []) > 8 else "finish"


def node_rerank_recommend(state: MusicState) -> dict[str, Any]:
    ranked = state.get("ranked") or []
    understanding = state["understanding"]
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "你是论坛帖子配乐精排节点。候选均可能相关，但最终最多保留 8 首最贴合帖子氛围与主题的歌曲。"
            "优先核心情绪与阅读场景，删除泛化或弱相关项。可以少于 8 首。"
            "score 必须是 0 到 1 的小数，不要用百分制。只输出合法 JSON。",
        ),
        (
            "human",
            "帖子理解：{understanding}\n待精排候选：{candidates}\n"
            "输出字段 tracks（musicKey、score、reason）与 rationale。",
        ),
    ])
    try:
        raw, usage = _structured_completion(prompt, {
            "understanding": understanding.model_dump_json(),
            "candidates": json.dumps([item.model_dump() for item in ranked], ensure_ascii=False),
        }, temperature=0)
        payload = json.loads(raw)
        reranked = MusicSelection.model_validate(parse_music_selection_payload(payload)).tracks
        valid = _validate_ranked(reranked, state.get("candidates") or [], threshold=0.62, limit=8)
        rationale = str(payload.get("rationale") or state.get("rationale") or "").strip()[:240]
        return {
            "ranked": valid,
            "rationale": rationale,
            "usages": [usage_item(usage, "music_recommend_rerank")],
        }
    except Exception:
        logger.exception("配乐推荐精排失败，采用初筛最高分结果")
        return {"ranked": sorted(ranked, key=lambda item: item.score, reverse=True)[:8]}


def node_parse_search_intent(state: MusicState) -> dict[str, Any]:
    query = state.get("query", "").strip()
    scope_label = _scope_label(state.get("scope") or "all")
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "你是音乐搜索意图解析节点。把用户自然语言 query 解析为结构化意图。"
            "genre 与 artist 必须是字符串；无明确歌手时 artist 填空字符串。"
            "当前搜索范围是「{scope_label}」：综合可看歌名/歌手/专辑；"
            "歌名只围绕标题；歌手只围绕艺人；专辑只围绕专辑名。"
            "只输出合法 JSON，字段 moods、genre、artist、keywords、summary。",
        ),
        ("human", "搜索范围：{scope_label}\n用户搜索：{query}"),
    ])
    try:
        raw, usage = _structured_completion(
            prompt, {"query": query[:240], "scope_label": scope_label}, temperature=0)
        intent = SearchIntent.model_validate(parse_search_intent_payload(raw))
        return {
            "intent": intent,
            "moods": intent.moods,
            "usages": [usage_item(usage, "music_search_intent")],
        }
    except Exception:
        logger.exception("音乐搜索意图解析失败")
        return {
            "intent": SearchIntent(summary=query[:200], keywords=[query[:40]] if query else []),
            "moods": [],
        }


def node_select_search(state: MusicState) -> dict[str, Any]:
    candidates = state.get("candidates") or []
    if not candidates:
        return {"ranked": [], "rationale": "候选曲库为空"}
    intent = state.get("intent") or SearchIntent()
    scope = _normalize_search_scope(state.get("scope") or "all")
    scope_label = _scope_label(scope)
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "你是论坛音乐 AI 搜索选曲节点。只能从候选中选择，禁止创造新 musicKey。"
            "必须严格遵守搜索范围「{scope_label}」："
            "综合可匹配 title/artist/album；歌名只按 title；歌手只按 artist；专辑只按 album。"
            "按用户描述选曲，匹配不足可返回 0 首；宁缺毋滥，通常 3-8 首，最多 12 首。"
            "score 必须是 0 到 1 的小数，不要用百分制。只输出合法 JSON。",
        ),
        (
            "human",
            "搜索范围：{scope_label}\n搜索意图：{intent}\n候选歌曲：{candidates}\n"
            "输出字段 tracks（musicKey、score、reason）与 rationale。",
        ),
    ])
    try:
        raw, usage = _structured_completion(prompt, {
            "scope_label": scope_label,
            "intent": intent.model_dump_json(),
            "candidates": json.dumps(candidates, ensure_ascii=False),
        }, temperature=0.05)
        payload = json.loads(raw)
        selected = MusicSelection.model_validate(parse_music_selection_payload(payload)).tracks
        ranked = _validate_ranked(selected, candidates, threshold=0.58, limit=12)
        rationale = str(payload.get("rationale") or intent.summary).strip()[:240]
        return {
            "ranked": ranked,
            "rationale": rationale,
            "usages": [usage_item(usage, "music_search_select")],
        }
    except Exception:
        logger.exception("音乐搜索选曲失败")
        return {"ranked": [], "rationale": ""}


def node_finish(state: MusicState) -> dict[str, Any]:
    ranked = sorted(state.get("ranked") or [], key=lambda item: item.score, reverse=True)
    return {"music_keys": [item.musicKey for item in ranked[:12]]}


def _structured_completion(
    prompt: ChatPromptTemplate,
    values: dict[str, Any],
    *,
    temperature: float,
) -> tuple[str, dict[str, Any]]:
    messages = prompt.format_messages(**values)
    return json_chat_completion(
        flash_model_name(),
        lc_messages_to_openai(messages),
        temperature=temperature,
        timeout=120,
    )


def _normalize_candidates(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in candidates[:200]:
        if not isinstance(item, dict):
            continue
        music_key = str(item.get("musicKey") or item.get("music_key") or "").strip()[:128]
        name = str(item.get("name") or item.get("title") or music_key).strip()[:80]
        if not music_key or music_key in seen:
            continue
        seen.add(music_key)
        title = str(item.get("title") or name).strip()[:80]
        artist = str(item.get("artist") or "").strip()[:80]
        album = str(item.get("album") or "").strip()[:80]
        result.append({
            "musicKey": music_key,
            "name": name,
            "title": title,
            "artist": artist,
            "album": album,
        })
    return result


def _normalize_search_scope(scope: str | None) -> str:
    value = str(scope or "all").strip().lower()
    if value in {"title", "artist", "album"}:
        return value
    return "all"


def _scope_label(scope: str) -> str:
    return {
        "all": "综合",
        "title": "歌名",
        "artist": "歌手",
        "album": "专辑",
    }.get(_normalize_search_scope(scope), "综合")


def _validate_ranked(
    ranked: list[RankedMusic],
    candidates: list[dict[str, Any]],
    *,
    threshold: float,
    limit: int,
) -> list[RankedMusic]:
    allowed = {str(item["musicKey"]) for item in candidates}
    return filter_ranked(
        ranked,
        allowed,
        key_fn=lambda item: item.musicKey.strip(),
        score_fn=lambda item: item.score,
        threshold=threshold,
        limit=limit,
    )


def _topics_to_moods(topics: list[str]) -> list[str]:
    return unique_clip(topics, limit=8, max_len=16)


_recommend_builder = StateGraph(MusicState)
_recommend_builder.add_node("understand", node_understand_article)
_recommend_builder.add_node("select", node_select_recommend)
_recommend_builder.add_node("rerank", node_rerank_recommend)
_recommend_builder.add_node("finish", node_finish)
_recommend_builder.add_edge(START, "understand")
_recommend_builder.add_edge("understand", "select")
_recommend_builder.add_conditional_edges(
    "select",
    route_after_recommend_selection,
    {"rerank": "rerank", "finish": "finish"},
)
_recommend_builder.add_edge("rerank", "finish")
_recommend_builder.add_edge("finish", END)
_RECOMMEND_GRAPH = _recommend_builder.compile()

_search_builder = StateGraph(MusicState)
_search_builder.add_node("intent", node_parse_search_intent)
_search_builder.add_node("select", node_select_search)
_search_builder.add_node("finish", node_finish)
_search_builder.add_edge(START, "intent")
_search_builder.add_edge("intent", "select")
_search_builder.add_edge("select", "finish")
_search_builder.add_edge("finish", END)
_SEARCH_GRAPH = _search_builder.compile()
