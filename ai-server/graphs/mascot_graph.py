"""
看板娘对话 — LangGraph 编排:

  START -> assess -> [tavily_search?] -> agent -> END

- writing: 本地站点文档可答则不搜；否则按需 MCP(Tavily)
- help: 仅用站点帮助，不 MCP
- reading: 暂不 MCP
"""
from __future__ import annotations

import json
import logging
import re
from typing import Any, Literal, TypedDict

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from clients.dashscope_chat_client import dashscope_chat_completion, lc_messages_to_openai
from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_messages
from config import settings
from mcp.registry import invoke_tool
from utils.mcp_routing import assess_mcp_for_writing
from utils.site_help import get_site_help_snippet

logger = logging.getLogger(__name__)


class MascotState(TypedDict, total=False):
    message: str
    session_id: str
    appearance: str
    tier: str
    vip_tier: int
    skill: str
    llm_route: str
    history: list[dict[str, str]]
    need_mcp_search: bool
    mcp_query: str
    mcp_context: str
    local_kb_snippet: str
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
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def _vip_tier_num(state: MascotState) -> int:
    vt = state.get("vip_tier")
    if isinstance(vt, int):
        return max(0, vt)
    tier = (state.get("tier") or "").strip().lower()
    if tier in ("max", "vip_max", "2"):
        return 2
    if tier in ("pro", "vip", "vip_pro", "1"):
        return 1
    return 0


def _normalize_llm_route(raw: str | None, vip_tier: int, skill: str) -> str:
    s = (raw or "").strip().lower().replace("_", "-")
    legacy = {
        "qwen": "qwen-flash",
        "deepseek": "deepseek-flash",
        "openai": "qwen-flash",
        "gemini": "gemini-deep",
        "gemini-flash": "gemini-deep",
        "claude": "claude-haiku",
        "claude-flash": "claude-haiku",
        "claude-deep": "claude-sonnet",
    }
    if s in legacy:
        s = legacy[s]

    valid_flash = {
        "qwen-flash", "deepseek-flash", "claude-haiku",
    }
    valid_deep = {
        "qwen-deep", "deepseek-deep", "gemini-deep", "claude-sonnet",
    }
    pro_routes = valid_flash | valid_deep
    max_only = {"claude-sonnet"}

    if s not in pro_routes:
        s = "qwen-flash"

    if s in max_only and vip_tier < 2:
        s = "claude-haiku" if vip_tier >= 1 else "qwen-flash"
    elif s in valid_deep and vip_tier < 1:
        s = s.replace("-deep", "-flash").replace("-sonnet", "-haiku")
    elif s in {"gemini-deep", "claude-haiku", "claude-sonnet"} and vip_tier < 1:
        s = "qwen-flash"

    if skill == "help":
        if s not in {"qwen-flash", "deepseek-flash"}:
            s = "qwen-flash"
        if s in valid_deep or s.startswith("gemini") or s.startswith("claude"):
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


def _huanapi_invoke(model: str, msgs: list[Any], *, use_claude_key: bool = False) -> tuple[str, dict[str, Any]]:
    hu = settings.huanapi
    base = str(hu.get("base_url") or "https://www.huanapi.com")
    if use_claude_key:
        key = (hu.get("claude_key") or hu.get("gemini_key") or "").strip()
    else:
        key = (hu.get("gemini_key") or "").strip()
    openai_msgs = _lc_to_openai_messages(msgs)
    text, usage = huanapi_messages(base, key, model, openai_msgs)
    usage["model_code"] = model
    return text, usage


def _invoke_mascot_llm(route: str, msgs: list[Any]) -> tuple[str, dict[str, Any]]:
    ds = settings.dashscope
    dsk = settings.deepseek
    hu = settings.huanapi
    openai_msgs = _lc_to_openai_messages(msgs)

    if route == "qwen-flash":
        model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
        return dashscope_chat_completion(model, lc_messages_to_openai(msgs), temperature=0.6)

    if route == "qwen-deep":
        model = str(ds.get("model_text_deep") or "qwen3.7-max")
        return dashscope_chat_completion(model, lc_messages_to_openai(msgs), temperature=0.55)

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

    if route == "gemini-deep":
        model = str(hu.get("model_gemini_deep") or "gemini-3.1-pro")
        return _huanapi_invoke(model, msgs)

    if route == "claude-haiku":
        model = str(hu.get("model_claude_haiku") or "claude-haiku-4-5")
        return _huanapi_invoke(model, msgs, use_claude_key=True)

    if route == "claude-sonnet":
        model = str(hu.get("model_claude_sonnet") or "claude-sonnet-4-6")
        return _huanapi_invoke(model, msgs, use_claude_key=True)

    raise ValueError(f"未知 mascot llm 路由: {route}")


def _skill_system(
    skill: str,
    tier: str,
    appearance: str,
    *,
    local_kb: str = "",
    mcp_context: str = "",
) -> str:
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
不要代写长文或生图。不要引用未提供的站外信息。"""
    if skill == "writing":
        extra = ""
        if local_kb:
            extra += f"\n【本站知识库（优先使用，无需编造）】\n{local_kb}\n"
        if mcp_context:
            extra += f"\n【联网检索参考（请据此组织写作，注明事实来源时可简述）】\n{mcp_context}\n"
        return base + """
你正在「写作」模式：可帮用户起草论坛帖文、润色、列提纲；回复可直接当草稿使用。""" + extra
    return base + """
basic 用户若要求重度能力，礼貌说明 VIP 功能；live2d.suggested_appearance 仅 legacy: standard|keyboard|gamepad 或 null。"""


def node_assess(state: MascotState) -> MascotState:
    skill = (state.get("skill") or "writing").lower()
    message = (state.get("message") or "").strip()
    out: MascotState = {
        "need_mcp_search": False,
        "mcp_query": "",
        "mcp_context": "",
        "local_kb_snippet": "",
    }
    if skill == "help" or skill == "reading":
        return out
    if skill == "writing":
        need, query, snippet = assess_mcp_for_writing(message)
        out["local_kb_snippet"] = snippet
        out["need_mcp_search"] = need
        out["mcp_query"] = query
    return out


def _route_after_assess(state: MascotState) -> Literal["tavily_search", "agent"]:
    if state.get("need_mcp_search") and (state.get("mcp_query") or "").strip():
        return "tavily_search"
    return "agent"


def node_tavily_search(state: MascotState) -> MascotState:
    query = (state.get("mcp_query") or state.get("message") or "").strip()
    ctx = ""
    try:
        ctx = invoke_tool("tavily_search", {"query": query})
    except Exception:
        logger.exception("MCP tavily_search 失败")
        ctx = ""
    return {"mcp_context": ctx}


def node_agent(state: MascotState) -> MascotState:
    tier = (state.get("tier") or "basic").lower()
    appearance = (state.get("appearance") or "snow_miku").lower()
    skill = (state.get("skill") or "writing").lower()
    if skill not in ("writing", "help", "reading"):
        skill = "writing"
    vip_tier = _vip_tier_num(state)
    llm_route = _normalize_llm_route(state.get("llm_route"), vip_tier, skill)
    user_msg = (state.get("message") or "").strip()
    history = state.get("history") or []
    max_turns = int(settings.mascot.get("max_history_turns", 8))

    sys = _skill_system(
        skill,
        tier,
        appearance,
        local_kb=state.get("local_kb_snippet") or "",
        mcp_context=state.get("mcp_context") or "",
    )

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
    g.add_node("assess", node_assess)
    g.add_node("tavily_search", node_tavily_search)
    g.add_node("agent", node_agent)
    g.add_edge(START, "assess")
    g.add_conditional_edges("assess", _route_after_assess, {"tavily_search": "tavily_search", "agent": "agent"})
    g.add_edge("tavily_search", "agent")
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
    vip_tier: int = 0,
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
            "vip_tier": vip_tier,
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
        "mcp_used": bool(out.get("mcp_context")),
    }
