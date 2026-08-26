"""音乐大厅个人品味推荐：单次 brief LLM → 关键词选曲（可维护、低费用）。"""

from __future__ import annotations

import json
import logging
import re
from typing import Annotated, Any

from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field
from typing_extensions import TypedDict
import operator

from clients.dashscope_chat_client import json_chat_completion, lc_messages_to_openai
from clients.llm import flash_model_name
from modules.creation.ranking import dedupe_clip
from modules.creation.usage import aggregate_usage, usage_item
from runtime.graph_run import invoke_with_fanout_limit

logger = logging.getLogger(__name__)


class TastePlan(BaseModel):
    queries: list[str] = Field(default_factory=list, max_length=5)
    preferGenres: list[str] = Field(default_factory=list, max_length=6)
    preferMoods: list[str] = Field(default_factory=list, max_length=8)
    avoidKeywords: list[str] = Field(default_factory=list, max_length=8)
    summary: str = Field(default="", max_length=200)


class MusicTasteState(TypedDict, total=False):
    favorites: list[dict[str, Any]]
    recent_plays: list[dict[str, Any]]
    extras: list[dict[str, Any]]
    candidates: list[dict[str, Any]]
    plan: TastePlan
    music_keys: list[str]
    rationale: str
    usages: Annotated[list[dict[str, Any]], operator.add]


def run_music_taste_graph(
    favorites: list[dict[str, Any]],
    recent_plays: list[dict[str, Any]],
    extras: list[dict[str, Any]],
    candidates: list[dict[str, Any]],
) -> dict[str, Any]:
    state = invoke_with_fanout_limit(_GRAPH, {
        "favorites": _clip_signal_list(favorites, 40),
        "recent_plays": _clip_signal_list(recent_plays, 40),
        "extras": _clip_signal_list(extras, 30),
        "candidates": _normalize_candidates(candidates),
        "usages": [],
    })
    keys = state.get("music_keys") or []
    return {
        "musicKeys": keys[:30],
        "rationale": str(state.get("rationale") or "")[:200],
        "usage": aggregate_usage(state.get("usages") or []),
    }


def node_brief(state: MusicTasteState) -> dict[str, Any]:
    """一次 LLM 产出 prefer/avoid/queries，供规则选曲使用。"""
    prompt = ChatPromptTemplate.from_messages([
        ("system",
         "你是音乐品味 brief 节点。根据收藏/近播/补充信号，输出 JSON："
         "queries(3到5条检索问句)、preferGenres、preferMoods、avoidKeywords、summary。"
         "只返回 JSON，不要 Markdown。"),
        ("human", "收藏：{favorites}\n近播：{recent}\n补充：{extras}"),
    ])
    fallback = TastePlan(
        queries=["用户常听风格相似歌曲"]
        + (_top_field(state.get("favorites") or [], "genre", 2) or ["轻快流行"])[:2],
        preferGenres=_top_field(state.get("favorites") or [], "genre", 4),
        preferMoods=_top_field(state.get("favorites") or [], "mood", 6),
        avoidKeywords=["过度重复同一艺人"],
        summary="基于收藏与近播的默认品味提纲",
    )
    try:
        messages = prompt.format_messages(
            favorites=_json_brief(state.get("favorites")),
            recent=_json_brief(state.get("recent_plays")),
            extras=_json_brief(state.get("extras")),
        )
        raw, usage_raw = json_chat_completion(
            flash_model_name(),
            lc_messages_to_openai(messages),
            temperature=0.2,
            timeout=120,
            retries=1,
        )
        plan = TastePlan.model_validate(json.loads(raw or "{}"))
        usage = usage_item(usage_raw, "music_taste_brief")
        return {"plan": plan, "usages": [usage] if usage else []}
    except Exception:
        logger.exception("music taste brief failed, using rule fallback")
        return {"plan": fallback}


def node_select(state: MusicTasteState) -> dict[str, Any]:
    plan = state.get("plan") or TastePlan()
    candidates = state.get("candidates") or []
    if not candidates:
        return {"music_keys": [], "rationale": "无候选曲目"}
    scored: list[tuple[float, str]] = []
    prefer = {x.lower() for x in (plan.preferGenres + plan.preferMoods) if x}
    avoid = {x.lower() for x in plan.avoidKeywords if x}
    query_blob = " ".join(plan.queries).lower()
    for item in candidates:
        key = str(item.get("musicKey") or "").strip()
        if not key:
            continue
        blob = " ".join([
            str(item.get("title") or ""),
            str(item.get("artist") or ""),
            str(item.get("genre") or ""),
            " ".join(item.get("moodTags") or []),
            str(item.get("aiProfile") or ""),
        ]).lower()
        score = 0.0
        for token in prefer:
            if token and token in blob:
                score += 2.0
        for token in avoid:
            if token and token in blob:
                score -= 1.5
        for word in re.findall(r"[\w\u4e00-\u9fff]{2,}", query_blob):
            if word in blob:
                score += 0.4
        scored.append((score, key))
    scored.sort(key=lambda x: x[0], reverse=True)
    keys: list[str] = []
    seen: set[str] = set()
    for _, key in scored:
        if key in seen:
            continue
        seen.add(key)
        keys.append(key)
        if len(keys) >= 30:
            break
    if len(keys) < 30:
        for item in candidates:
            key = str(item.get("musicKey") or "").strip()
            if not key or key in seen:
                continue
            seen.add(key)
            keys.append(key)
            if len(keys) >= 30:
                break
    return {
        "music_keys": keys[:30],
        "rationale": (plan.summary or "按品味意图从候选池挑选")[:200],
    }


def _normalize_candidates(items: list[dict[str, Any]] | None) -> list[dict[str, Any]]:
    return dedupe_clip(
        items,
        key_field="musicKey",
        fields={
            "musicKey": 128,
            "title": 80,
            "artist": 80,
            "genre": 40,
            "moodTags": 20,
            "aiProfile": 240,
        },
        limit=200,
    )


def _clip_signal_list(items: list[dict[str, Any]] | None, limit: int) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for item in items or []:
        if not isinstance(item, dict):
            continue
        out.append({
            "musicKey": str(item.get("musicKey") or "")[:128],
            "title": str(item.get("title") or "")[:80],
            "artist": str(item.get("artist") or "")[:80],
            "genre": str(item.get("genre") or "")[:40],
            "mood": str(item.get("mood") or "")[:40],
        })
        if len(out) >= limit:
            break
    return out


def _json_brief(items: list[dict[str, Any]] | None) -> str:
    return json.dumps(items or [], ensure_ascii=False)[:2500]


def _top_field(items: list[dict[str, Any]], field: str, limit: int) -> list[str]:
    counter: dict[str, int] = {}
    for item in items:
        value = str(item.get(field) or "").strip()
        if not value:
            continue
        counter[value] = counter.get(value, 0) + 1
    ranked = sorted(counter.items(), key=lambda x: x[1], reverse=True)
    return [name for name, _ in ranked[:limit]]


_builder = StateGraph(MusicTasteState)
_builder.add_node("brief", node_brief)
_builder.add_node("select", node_select)
_builder.add_edge(START, "brief")
_builder.add_edge("brief", "select")
_builder.add_edge("select", END)
_GRAPH = _builder.compile()
