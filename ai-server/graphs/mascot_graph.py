"""
看板娘对话 — 极简 LangGraph: START -> agent -> END.

小项目: 无 Postgres checkpoint; 会话由 Java/前端用 history 数组携带.
"""
from __future__ import annotations

import json
import logging
import re
from typing import Any, TypedDict

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from clients.dashscope_chat_client import dashscope_chat_completion, lc_messages_to_openai
from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_messages, normalize_huanapi_v1_base
from config import settings

logger = logging.getLogger(__name__)

from utils.site_help import get_site_help_snippet


class MascotState(TypedDict, total=False):
    message: str
    session_id: str
    appearance: str
    tier: str
    skill: str
    llm_route: str
    history: list[dict[str, str]]
    reply: str
    live2d: dict[str, Any]
    suggested_appearance: str | None
    usage: dict[str, Any]


def _parse_json_object(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    if not text:
        return None
    m = re.search(r"\{[\s\S]*\}\s*$", text)
    if not m:
        m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    raw = m.group(0)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


def _extract_text(resp) -> str:
    content = getattr(resp, "content", resp)
    if isinstance(content, list) and content:
        first = content[0]
        return first.get("text", "") if isinstance(first, dict) else str(first)
    if isinstance(content, str):
        return content
    return str(content or "")


def _qwen_usage_from_resp(resp, model_code: str) -> dict[str, Any]:
    meta = getattr(resp, "response_metadata", None) or {}
    if not isinstance(meta, dict):
        meta = {}
    tu = meta.get("token_usage") or meta.get("usage") or {}
    if not isinstance(tu, dict):
        tu = {}
    inp = int(tu.get("input_tokens") or tu.get("prompt_tokens") or 0)
    out = int(tu.get("output_tokens") or tu.get("completion_tokens") or 0)
    return {
        "model_code": model_code,
        "input_tokens": inp,
        "output_tokens": out,
        "images": 0,
        "estimated": inp == 0 and out == 0,
    }


def _normalize_llm_route(raw: str | None, user_tier: str, skill: str) -> str:
    s = (raw or "").strip().lower().replace("_", "-")
    if s in {"openai", "claude"}:
        s = ""
    legacy = {"qwen": "qwen-flash", "deepseek": "deepseek-flash", "gemini": "gemini-flash"}
    if s in legacy:
        s = legacy[s]
    valid_flash = {"qwen-flash", "deepseek-flash", "gemini-flash"}
    valid_deep = {"qwen-deep", "deepseek-deep", "gemini-deep"}
    if s not in valid_flash and s not in valid_deep:
        s = "qwen-flash"
    if s in valid_deep and user_tier != "vip":
        s = s.replace("-deep", "-flash")
    if skill == "help":
        if s not in valid_flash:
            s = "qwen-flash"
        if s in valid_deep:
            s = "qwen-flash"
    return s


def _lc_to_openai_messages(msgs: list[Any]) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    for m in msgs:
        if isinstance(m, SystemMessage):
            out.append({"role": "system", "content": str(m.content)})
        elif isinstance(m, HumanMessage):
            out.append({"role": "user", "content": str(m.content)})
        elif isinstance(m, AIMessage):
            out.append({"role": "assistant", "content": str(m.content)})
    return out


def _invoke_mascot_llm(route: str, msgs: list[Any]) -> tuple[str, dict[str, Any]]:
    ds = settings.dashscope
    dsk = settings.deepseek
    hu = settings.huanapi
    openai_msgs = _lc_to_openai_messages(msgs)

    if route == "qwen-flash":
        model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
        text, usage = dashscope_chat_completion(
            model, lc_messages_to_openai(msgs), temperature=0.6,
        )
        return text, usage

    if route == "qwen-deep":
        model = str(ds.get("model_text_deep") or "qwen3.6-max-preview")
        text, usage = dashscope_chat_completion(
            model, lc_messages_to_openai(msgs), temperature=0.55,
        )
        return text, usage

    if route == "deepseek-flash":
        model = str(dsk.get("model_flash") or "deepseek-v4-flash")
        base = str(dsk.get("base_url") or "https://api.deepseek.com/v1")
        key = str(dsk.get("api_key") or "")
        return deepseek_chat_completion(base, key, model, openai_msgs)

    if route == "deepseek-deep":
        model = str(dsk.get("model_pro") or "deepseek-v4-pro")
        base = str(dsk.get("base_url") or "https://api.deepseek.com/v1")
        key = str(dsk.get("api_key") or "")
        return deepseek_chat_completion(base, key, model, openai_msgs)

    if route == "gemini-flash":
        model = str(hu.get("model_gemini_flash") or "gemini-3-flash")
        base = normalize_huanapi_v1_base(str(hu.get("base_url") or "https://www.huanapi.com"))
        key = str(hu.get("gemini_key") or "")
        return huanapi_messages(base, key, model, openai_msgs)

    if route == "gemini-deep":
        model = str(hu.get("model_gemini_pro") or "gemini-3.1-pro")
        base = normalize_huanapi_v1_base(str(hu.get("base_url") or "https://www.huanapi.com"))
        key = str(hu.get("gemini_key") or "")
        return huanapi_messages(base, key, model, openai_msgs)

    raise ValueError(f"未知 mascot llm 路由: {route}")


def _skill_system(skill: str, tier: str, appearance: str) -> str:
    base = f"""你是论坛网站的看板娘助手，用自然、简短的中文回复。

当前用户档位 tier={tier}（basic=普通用户, vip=会员/管理员体验档）。
用户当前选择的 Live2D 模型代码 mascot_model={appearance}（仅作人设上下文）。
当前功能 skill={skill}（writing=写作代笔, help=站点帮助, reading=伴读）。

**必须只输出一段合法 JSON**，不要 Markdown 代码围栏:
{{"reply":"...","live2d":{{}},"suggested_appearance":null}}
"""
    if skill == "help":
        return base + f"""
你正在「站点帮助」模式：用简短条目回答论坛使用问题，可参考:
{get_site_help_snippet()}
不要代写长文或生图。"""
    if skill == "writing":
        return base + """
你正在「写作」模式：可帮用户起草论坛帖文、润色、列提纲；回复可直接当草稿使用。"""
    return base + """
basic 用户若要求重度能力，礼貌说明 VIP 功能；live2d.suggested_appearance 仅 legacy: standard|keyboard|gamepad 或 null。"""

def node_agent(state: MascotState) -> MascotState:
    tier = (state.get("tier") or "basic").lower()
    appearance = (state.get("appearance") or "snow_miku").lower()
    skill = (state.get("skill") or "writing").lower()
    if skill not in ("writing", "help", "reading"):
        skill = "writing"
    llm_route = _normalize_llm_route(state.get("llm_route"), tier, skill)
    user_msg = (state.get("message") or "").strip()
    history = state.get("history") or []
    max_turns = int(settings.mascot.get("max_history_turns", 8))

    sys = _skill_system(skill, tier, appearance)

    msgs: list = [SystemMessage(content=sys)]
    for item in history[-max_turns:]:
        role = (item.get("role") or "").lower()
        content = (item.get("content") or "").strip()
        if not content:
            continue
        if role == "assistant":
            msgs.append(AIMessage(content=content))
        elif role == "user":
            msgs.append(HumanMessage(content=content))
    msgs.append(HumanMessage(content=user_msg))

    usage: dict[str, Any] = {}
    try:
        raw, usage = _invoke_mascot_llm(llm_route, msgs)
    except Exception:
        logger.exception("看板娘 LLM 调用失败 route=%s", llm_route)
        return {
            "reply": "我现在有点累了，稍后再来找我玩吧～",
            "live2d": {},
            "suggested_appearance": None,
            "usage": usage,
        }

    data = _parse_json_object(raw)
    if not isinstance(data, dict):
        return {
            "reply": raw[:2000] if raw else "……",
            "live2d": {},
            "suggested_appearance": None,
            "usage": usage,
        }

    reply = str(data.get("reply", "")).strip() or "嗯嗯～"
    live2d = data.get("live2d")
    if not isinstance(live2d, dict):
        live2d = {}
    sug = data.get("suggested_appearance")
    if sug not in (None, "standard", "keyboard", "gamepad"):
        sug = None

    return {
        "reply": reply[:4000],
        "live2d": live2d,
        "suggested_appearance": sug,
        "usage": usage,
    }


def build_mascot_graph():
    g = StateGraph(MascotState)
    g.add_node("agent", node_agent)
    g.add_edge(START, "agent")
    g.add_edge("agent", END)
    return g.compile()


_GRAPH = None


def run_mascot_chat(
    *,
    message: str,
    session_id: str,
    appearance: str,
    tier: str,
    history: list[dict[str, str]] | None,
    llm_provider: str = "",
    skill: str = "writing",
) -> dict[str, Any]:
    global _GRAPH
    if _GRAPH is None:
        _GRAPH = build_mascot_graph()
    out = _GRAPH.invoke(
        {
            "message": message,
            "session_id": session_id or "",
            "appearance": appearance or "snow_miku",
            "tier": tier or "basic",
            "skill": skill or "writing",
            "llm_route": llm_provider or "",
            "history": history or [],
        }
    )
    return {
        "reply": out.get("reply", ""),
        "live2d": out.get("live2d") or {},
        "suggested_appearance": out.get("suggested_appearance"),
        "usage": out.get("usage") or {},
    }
