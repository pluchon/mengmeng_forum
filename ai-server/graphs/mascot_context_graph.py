"""看板娘上下文压缩图：只保留可继续对话的事实和未完成事项。"""

from __future__ import annotations

from typing import Any, TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from clients.dashscope_chat_client import dashscope_chat_completion, lc_messages_to_openai
from config import settings


class ContextState(TypedDict, total=False):
    history: list[dict[str, str]]
    summary: str
    usage: dict[str, Any]


def _summarize(state: ContextState) -> ContextState:
    history_lines = []
    for item in state.get("history") or []:
        role = str(item.get("role") or "").strip().lower()
        content = str(item.get("content") or "").strip()
        if role in {"user", "assistant"} and content:
            history_lines.append(f"{role}: {content[:3000]}")
    model = str(settings.dashscope.get("model_text_flash") or settings.dashscope.get("model_text") or "qwen3.7-flash")
    messages = [
        SystemMessage(content=(
            "将论坛看板娘对话压缩成简洁中文记忆。只保留用户偏好、已确认事实、未完成请求、"
            "重要结论与生成内容线索；不要编造，不要使用表情或客套，不超过 1600 字。"
        )),
        HumanMessage(content="\n".join(history_lines)),
    ]
    summary, usage = dashscope_chat_completion(model, lc_messages_to_openai(messages), temperature=0.1)
    return {"summary": summary.strip()[:6000], "usage": usage}


_GRAPH = None


def compress_mascot_context(history: list[dict[str, str]]) -> dict[str, Any]:
    global _GRAPH
    if _GRAPH is None:
        graph = StateGraph(ContextState)
        graph.add_node("summarize", _summarize)
        graph.add_edge(START, "summarize")
        graph.add_edge("summarize", END)
        _GRAPH = graph.compile()
    result = _GRAPH.invoke({"history": history})
    return {"summary": result.get("summary") or "", "usage": result.get("usage") or {}}
