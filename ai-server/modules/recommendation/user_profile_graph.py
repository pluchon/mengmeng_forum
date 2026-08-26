"""用户推荐画像 LangGraph：准备信号 → 正/负主题并行 → 合并 → 偏好查询句。"""

from __future__ import annotations

import operator
from typing import Annotated, Any, Literal

from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import END, START, StateGraph
from langgraph.types import Send
from typing_extensions import TypedDict

from clients.llm import text_llm
from config import settings
from runtime.ai_runtime import AiRuntime
from runtime.graph_run import invoke_with_fanout_limit

from .service import (
    _FEATURE_VERSION,
    _json_object,
    _name_list,
    _negative_signals,
    _normalize_topics,
    _optional_text,
    _signals,
    _topics_from_names,
)

_runtime = AiRuntime()
AgentKind = Literal["positive", "avoid"]


class UserProfileState(TypedDict, total=False):
    explicit: list[str]
    recent7: list[dict[str, Any]]
    recent14: list[dict[str, Any]]
    negative_recent7: list[dict[str, Any]]
    negative_recent14: list[dict[str, Any]]
    allowed_topics: list[str]
    allowed_avoid: list[str]
    topic_drafts: Annotated[list[dict[str, Any]], operator.add]
    avoid_drafts: Annotated[list[dict[str, Any]], operator.add]
    summary_drafts: Annotated[list[str], operator.add]
    used_fallback: Annotated[bool, operator.or_]
    topics: list[dict[str, Any]]
    avoid_topics: list[dict[str, Any]]
    summary: str
    preference_query: str
    generated_by: str
    trace_id: str


def run_user_profile_graph(payload: dict[str, Any], trace_id: str) -> dict[str, Any]:
    state = invoke_with_fanout_limit(_GRAPH, {
        "explicit": _name_list(payload.get("explicitBoards")),
        "recent7": _signals(payload.get("recent7")),
        "recent14": _signals(payload.get("recent14")),
        "negative_recent7": _negative_signals(payload.get("negativeRecent7")),
        "negative_recent14": _negative_signals(payload.get("negativeRecent14")),
        "topic_drafts": [],
        "avoid_drafts": [],
        "summary_drafts": [],
        "used_fallback": False,
        "trace_id": trace_id or "",
    })
    return {
        "featureVersion": _FEATURE_VERSION,
        "topics": state.get("topics") or [],
        "avoidTopics": state.get("avoid_topics") or [],
        "summary": state.get("summary") or "",
        "preferenceQuery": state.get("preference_query") or "",
        "generatedBy": state.get("generated_by") or "RULE_FALLBACK",
    }


def node_prepare_signals(state: UserProfileState) -> dict[str, Any]:
    explicit = list(state.get("explicit") or [])
    recent7 = list(state.get("recent7") or [])
    recent14 = list(state.get("recent14") or [])
    negative_recent7 = list(state.get("negative_recent7") or [])
    negative_recent14 = list(state.get("negative_recent14") or [])
    allowed_topics = explicit + [item["board"] for item in recent7] + [item["board"] for item in recent14]
    allowed_avoid = [item["board"] for item in negative_recent7] + [item["board"] for item in negative_recent14]
    return {
        "explicit": explicit,
        "recent7": recent7,
        "recent14": recent14,
        "negative_recent7": negative_recent7,
        "negative_recent14": negative_recent14,
        "allowed_topics": list(dict.fromkeys(item for item in allowed_topics if item)),
        "allowed_avoid": list(dict.fromkeys(item for item in allowed_avoid if item)),
    }


def fanout_topic_agents(state: UserProfileState) -> list[Send]:
    base = {
        "explicit": state.get("explicit") or [],
        "recent7": state.get("recent7") or [],
        "recent14": state.get("recent14") or [],
        "negative_recent7": state.get("negative_recent7") or [],
        "negative_recent14": state.get("negative_recent14") or [],
        "allowed_topics": state.get("allowed_topics") or [],
        "allowed_avoid": state.get("allowed_avoid") or [],
        "trace_id": state.get("trace_id") or "",
    }
    return [
        Send("positive_topics_agent", {**base, "agent_kind": "positive"}),
        Send("avoid_topics_agent", {**base, "agent_kind": "avoid"}),
    ]


def node_positive_topics_agent(state: dict[str, Any]) -> dict[str, Any]:
    return _run_topic_agent("positive", state)


def node_avoid_topics_agent(state: dict[str, Any]) -> dict[str, Any]:
    return _run_topic_agent("avoid", state)


def _run_topic_agent(kind: AgentKind, state: dict[str, Any]) -> dict[str, Any]:
    allowed_list = list(
        (state.get("allowed_topics") if kind == "positive" else state.get("allowed_avoid")) or []
    )
    if not allowed_list:
        return {"topic_drafts": [], "avoid_drafts": [], "summary_drafts": [], "used_fallback": False}

    fallback_topics = _topics_from_names(allowed_list)
    if kind == "positive":
        prompt = ChatPromptTemplate.from_messages([
            ("system",
             "你是论坛用户正向兴趣整理器。只返回 JSON，不要 Markdown。"
             "topics.name 只能从输入板块中选择且原样输出，至多 5 项，weight 在 0 到 1。"
             "近 7 天权重高于第 8 至 14 天；手选兴趣优先。不得猜测敏感属性。"),
            ("human",
             "手选兴趣：{explicit}\n近7天正向聚合：{recent7}\n第8至14天正向聚合：{recent14}\n"
             "输出 JSON：{{\"topics\":[{{\"name\":\"\",\"weight\":0.0}}],\"summary\":\"\"}}"),
        ])
        raw = _invoke_llm(prompt, {
            "explicit": state.get("explicit") or [],
            "recent7": state.get("recent7") or [],
            "recent14": state.get("recent14") or [],
        }, str(state.get("trace_id") or ""), {
            "topics": fallback_topics,
            "summary": "基于近期公开互动生成的内容偏好",
        })
        topics = _normalize_topics(raw.get("topics"), set(allowed_list)) or fallback_topics
        summary = _optional_text(raw.get("summary"), 80) or "基于近期公开互动生成的内容偏好"
        return {
            "topic_drafts": topics,
            "summary_drafts": [summary],
            "used_fallback": bool(raw.get("_fallback")),
        }

    prompt = ChatPromptTemplate.from_messages([
        ("system",
         "你是论坛用户负向反馈整理器。只返回 JSON，不要 Markdown。"
         "avoidTopics.name 只能从负向输入板块中选择且原样输出，至多 5 项，weight 在 0 到 1。"
         "近 7 天权重高于第 8 至 14 天。不得猜测敏感属性。"),
        ("human",
         "近7天负向反馈：{negative_recent7}\n第8至14天负向反馈：{negative_recent14}\n"
         "输出 JSON：{{\"avoidTopics\":[{{\"name\":\"\",\"weight\":0.0}}]}}"),
    ])
    raw = _invoke_llm(prompt, {
        "negative_recent7": state.get("negative_recent7") or [],
        "negative_recent14": state.get("negative_recent14") or [],
    }, str(state.get("trace_id") or ""), {
        "avoidTopics": fallback_topics,
    })
    avoid_topics = _normalize_topics(raw.get("avoidTopics"), set(allowed_list)) or fallback_topics
    return {
        "avoid_drafts": avoid_topics,
        "used_fallback": bool(raw.get("_fallback")),
    }


def node_merge_normalize(state: UserProfileState) -> dict[str, Any]:
    allowed_topics = set(state.get("allowed_topics") or [])
    allowed_avoid = set(state.get("allowed_avoid") or [])
    topics = _normalize_topics(state.get("topic_drafts"), allowed_topics) or _topics_from_names(
        list(state.get("allowed_topics") or [])
    )
    avoid_topics = _normalize_topics(state.get("avoid_drafts"), allowed_avoid) or _topics_from_names(
        list(state.get("allowed_avoid") or [])
    )
    summaries = [item for item in (state.get("summary_drafts") or []) if item]
    summary = summaries[0] if summaries else "基于近期公开互动生成的内容偏好"
    generated_by = "RULE_FALLBACK" if state.get("used_fallback") else "AI"
    if not topics and not avoid_topics:
        generated_by = "RULE_FALLBACK"
    return {
        "topics": topics[:5],
        "avoid_topics": avoid_topics[:5],
        "summary": summary[:80],
        "generated_by": generated_by,
    }


def node_build_preference_query(state: UserProfileState) -> dict[str, Any]:
    parts: list[str] = []
    for name in state.get("explicit") or []:
        text = _optional_text(name, 32)
        if text:
            parts.append(text)
    for topic in state.get("topics") or []:
        if not isinstance(topic, dict):
            continue
        text = _optional_text(topic.get("name"), 32)
        if text and text not in parts:
            parts.append(text)
    summary = _optional_text(state.get("summary"), 80)
    if summary:
        parts.append(summary)
    return {"preference_query": " ".join(parts).strip()[:200]}


def _invoke_llm(
    prompt: ChatPromptTemplate,
    values: dict[str, Any],
    trace_id: str,
    fallback: dict[str, Any],
) -> dict[str, Any]:
    model_name = str(settings.dashscope.get("model_text_flash") or "qwen3.7-flash")
    response = _runtime.call_llm(
        lambda: (prompt | text_llm(temperature=0.1, model_name=model_name)).invoke(values),
        trace_id=trace_id,
        model_name=model_name,
        retries=1,
        fallback=lambda: {**fallback, "_fallback": True},
        fallback_model_name="rules-fallback",
    )
    if isinstance(response, dict):
        return response
    content = getattr(response, "content", response)
    parsed = _json_object(content)
    return parsed if parsed else {**fallback, "_fallback": True}


def _build_graph():
    graph = StateGraph(UserProfileState)
    graph.add_node("prepare_signals", node_prepare_signals)
    graph.add_node("positive_topics_agent", node_positive_topics_agent)
    graph.add_node("avoid_topics_agent", node_avoid_topics_agent)
    graph.add_node("merge_normalize", node_merge_normalize)
    graph.add_node("build_preference_query", node_build_preference_query)
    graph.add_edge(START, "prepare_signals")
    graph.add_conditional_edges(
        "prepare_signals",
        fanout_topic_agents,
        ["positive_topics_agent", "avoid_topics_agent"],
    )
    graph.add_edge("positive_topics_agent", "merge_normalize")
    graph.add_edge("avoid_topics_agent", "merge_normalize")
    graph.add_edge("merge_normalize", "build_preference_query")
    graph.add_edge("build_preference_query", END)
    return graph.compile()


_GRAPH = _build_graph()
