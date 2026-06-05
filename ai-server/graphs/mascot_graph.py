"""
看板娘对话 — LangGraph 编排:

  START -> route_skill -> assess -> [tavily_search?] -> agent -> END

- skill=chat：自动路由为 writing | help
- writing：本地站点文档可答则不搜；否则按需 MCP(Tavily) / 地图
- help：仅用站点帮助，不 MCP
"""
from __future__ import annotations

import json
import logging
import re
import time
from typing import Any, Literal, TypedDict

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from clients.dashscope_chat_client import (
    dashscope_chat_completion,
    dashscope_stream_text,
    lc_messages_to_openai,
)
from clients.deepseek_client import deepseek_stream_text
from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_messages
from config import settings
from mcp.registry import invoke_tool
from utils.mascot_article_rag import fetch_related_articles, format_related_for_prompt
from utils.mascot_mcp_orchestrator import prepare_mascot_mcp_bundle
from utils.mascot_skill_router import route_mascot_skill
from utils.site_help import get_site_help_snippet

logger = logging.getLogger(__name__)


class MascotState(TypedDict, total=False):
    message: str
    session_id: str
    appearance: str
    tier: str
    vip_tier: int
    skill: str
    routed_skill: str
    llm_route: str
    history: list[dict[str, str]]
    need_mcp_search: bool
    mcp_query: str
    mcp_context: str
    local_kb_snippet: str
    client_datetime: str
    datetime_context: str
    travel_guidance: str
    travel_phase: str
    related_articles: list[dict[str, Any]]
    related_articles_prompt: str
    mcp_used: bool
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
        "claude": "claude-sonnet",
        "claude-flash": "claude-sonnet",
        "claude-deep": "claude-sonnet",
        "claude-haiku": "claude-sonnet",
    }
    if s in legacy:
        s = legacy[s]

    valid_flash = {
        "qwen-flash", "deepseek-flash",
    }
    valid_deep = {
        "qwen-deep", "deepseek-deep", "gemini-deep", "claude-sonnet",
    }
    pro_routes = valid_flash | valid_deep
    max_only = {"claude-sonnet"}

    if s not in pro_routes:
        s = "qwen-flash"

    if s in max_only and vip_tier < 2:
        s = "qwen-flash"
    elif s in valid_deep and vip_tier < 1:
        s = s.replace("-deep", "-flash")
    elif s in {"gemini-deep", "claude-sonnet"} and vip_tier < 1:
        s = "qwen-flash"

    if skill == "help":
        if s not in {"qwen-flash", "deepseek-flash"}:
            s = "qwen-flash"
        if s in valid_deep or s.startswith("gemini") or s.startswith("claude"):
            s = "qwen-flash"

    return s


def _effective_skill(state: dict[str, Any]) -> str:
    sk = (state.get("routed_skill") or state.get("skill") or "writing").lower()
    if sk not in ("writing", "help"):
        sk = "writing"
    return sk


def node_route_skill(state: MascotState) -> MascotState:
    skill = (state.get("skill") or "writing").lower()
    if skill in ("writing", "help"):
        return {"routed_skill": skill}
    if skill == "chat":
        routed = route_mascot_skill(
            (state.get("message") or "").strip(),
            state.get("history") or [],
        )
        return {"routed_skill": routed}
    return {"routed_skill": "writing"}


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
    from clients.usage_util import attach_latency

    t0 = time.perf_counter()
    ds = settings.dashscope
    dsk = settings.deepseek
    hu = settings.huanapi
    openai_msgs = _lc_to_openai_messages(msgs)

    if route == "qwen-flash":
        model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
        text, usage = dashscope_chat_completion(model, lc_messages_to_openai(msgs), temperature=0.6)
        return text, attach_latency(usage, t0)

    if route == "qwen-deep":
        model = str(ds.get("model_text_deep") or "qwen3.7-max")
        text, usage = dashscope_chat_completion(model, lc_messages_to_openai(msgs), temperature=0.55)
        return text, attach_latency(usage, t0)

    if route == "deepseek-flash":
        model = str(dsk.get("model_flash") or "deepseek-v4-flash")
        base = str(dsk.get("base_url") or "https://api.deepseek.com/v1")
        key = str(dsk.get("api_key") or "")
        text, usage = deepseek_chat_completion(base, key, model, openai_msgs)
        return text, attach_latency(usage, t0)

    if route == "deepseek-deep":
        model = str(dsk.get("model_pro") or "deepseek-v4-pro")
        base = str(dsk.get("base_url") or "https://api.deepseek.com/v1")
        key = str(dsk.get("api_key") or "")
        text, usage = deepseek_chat_completion(base, key, model, openai_msgs)
        return text, attach_latency(usage, t0)

    if route == "gemini-deep":
        model = str(hu.get("model_gemini_deep") or "gemini-3.1-pro")
        text, usage = _huanapi_invoke(model, msgs)
        return text, attach_latency(usage, t0)

    if route == "claude-sonnet":
        model = str(hu.get("model_claude_sonnet") or "claude-sonnet-4-6")
        text, usage = _huanapi_invoke(model, msgs, use_claude_key=True)
        return text, attach_latency(usage, t0)

    raise ValueError(f"未知 mascot llm 路由: {route}")


def _yield_stream_events(events: Any, *, model_code: str) -> Any:
    """消费 ('text'|'usage', payload) 事件流，yield (text_chunk, usage_or_none)。"""
    from clients.usage_util import attach_latency

    t0 = time.perf_counter()
    usage: dict[str, Any] = {}
    for kind, payload in events:
        if kind == "usage" and isinstance(payload, dict):
            usage = payload
            usage.setdefault("model_code", model_code)
            continue
        if kind == "text" and payload:
            yield str(payload), None
    if not usage:
        usage = {"model_code": model_code, "input_tokens": 0, "output_tokens": 0, "estimated": True}
    elif not usage.get("input_tokens") and not usage.get("output_tokens"):
        usage["estimated"] = True
    else:
        usage["estimated"] = False
    usage.setdefault("model_code", model_code)
    yield "", attach_latency(usage, t0)


def _invoke_mascot_llm_stream(route: str, msgs: list[Any]):
    """yield (text_chunk, usage_or_none)；usage 仅在最后一次非 None。"""
    ds = settings.dashscope
    dsk = settings.deepseek
    openai_msgs = _lc_to_openai_messages(msgs)

    if route in ("qwen-flash", "qwen-deep"):
        model = (
            str(ds.get("model_text_deep") or "qwen3.7-max")
            if route == "qwen-deep"
            else str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
        )
        temp = 0.55 if route == "qwen-deep" else 0.6
        events = dashscope_stream_text(model, lc_messages_to_openai(msgs), temperature=temp)
        yield from _yield_stream_events(events, model_code=model)
        return

    if route in ("deepseek-flash", "deepseek-deep"):
        model = (
            str(dsk.get("model_pro") or "deepseek-v4-pro")
            if route == "deepseek-deep"
            else str(dsk.get("model_flash") or "deepseek-v4-flash")
        )
        base = str(dsk.get("base_url") or "https://api.deepseek.com/v1")
        key = str(dsk.get("api_key") or "")
        events = deepseek_stream_text(base, key, model, openai_msgs)
        yield from _yield_stream_events(events, model_code=model)
        return

    text, usage = _invoke_mascot_llm(route, msgs)
    if text:
        yield text, None
    yield "", usage


def _prepare_mascot_context(
    *,
    message: str,
    appearance: str,
    tier: str,
    history: list[dict[str, str]] | None,
    llm_provider: str,
    skill: str,
    vip_tier: int,
    client_datetime: str = "",
) -> dict[str, Any]:
    state: MascotState = {
        "message": message,
        "session_id": "",
        "appearance": appearance or "snow_miku",
        "tier": tier or "basic",
        "vip_tier": vip_tier,
        "skill": skill or "writing",
        "llm_route": llm_provider or "",
        "history": history or [],
        "client_datetime": client_datetime or "",
    }
    state.update(node_route_skill(state))
    state.update(node_assess(state))
    if state.get("need_mcp_search") and (state.get("mcp_query") or "").strip():
        state.update(node_tavily_search(state))
    return state


def _build_agent_messages(state: dict[str, Any], *, stream: bool = False) -> tuple[str, list]:
    tier = (state.get("tier") or "basic").lower()
    appearance = (state.get("appearance") or "snow_miku").lower()
    skill = _effective_skill(state)
    vip_tier = _vip_tier_num(state)
    llm_route = _normalize_llm_route(state.get("llm_route"), vip_tier, skill)
    user_msg = (state.get("message") or "").strip()
    history = state.get("history") or []
    max_turns = int(settings.mascot.get("max_history_turns", 8))
    sys_fn = _skill_system_stream if stream else _skill_system
    sys = sys_fn(
        skill,
        tier,
        appearance,
        local_kb=state.get("local_kb_snippet") or "",
        mcp_context=state.get("mcp_context") or "",
        travel_guidance=state.get("travel_guidance") or "",
        related_articles_prompt=state.get("related_articles_prompt") or "",
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
    return llm_route, msgs


def stream_mascot_chat(
    *,
    message: str,
    session_id: str,
    appearance: str,
    tier: str,
    history: list[dict[str, str]] | None,
    llm_provider: str = "",
    skill: str = "chat",
    vip_tier: int = 0,
    client_datetime: str = "",
):
    """yield ('text', str) 或 ('usage', dict)。"""
    ctx = _prepare_mascot_context(
        message=message,
        appearance=appearance,
        tier=tier,
        history=history,
        llm_provider=llm_provider,
        skill=skill,
        vip_tier=vip_tier,
        client_datetime=client_datetime,
    )
    from clients.usage_util import attach_latency

    route, msgs = _build_agent_messages(ctx, stream=True)
    t0 = time.perf_counter()
    usage: dict[str, Any] = {}
    try:
        for piece, u in _invoke_mascot_llm_stream(route, msgs):
            if u:
                usage = u
            elif piece:
                yield ("text", piece)
    except Exception:
        logger.exception("看板娘流式 LLM 失败 route=%s", route)
        yield ("text", "我现在有点累了，稍后再来找我玩吧～")
        usage = {"model_code": route, "estimated": True}
    if not usage:
        usage = {"model_code": route, "estimated": True}
    yield ("usage", attach_latency(usage, t0))


def _skill_system_stream(
    skill: str,
    tier: str,
    appearance: str,
    *,
    local_kb: str = "",
    mcp_context: str = "",
    travel_guidance: str = "",
    related_articles_prompt: str = "",
) -> str:
    base = f"""你是论坛网站的看板娘助手，用自然、简短的中文回复。

当前用户档位 tier={tier}（basic=普通用户, vip=会员/管理员体验档）。
用户当前选择的 Live2D 模型代码 mascot_model={appearance}（仅作人设上下文）。
当前功能 skill={skill}（writing=写作代笔, help=站点帮助）。

请直接输出回复正文，不要使用 JSON 或代码围栏。
"""
    if skill == "help":
        return base + f"""
你正在「站点帮助」模式：用简短条目回答论坛使用问题，可参考:
{get_site_help_snippet()}
不要代写长文或生图。不要引用未提供的站外信息。"""
    if skill == "writing":
        extra = ""
        if related_articles_prompt:
            extra += f"\n【相关站内帖子（系统会在消息下方展示链接，可引用标题）】\n{related_articles_prompt}\n"
        if local_kb:
            extra += f"\n【本站知识库（优先使用，无需编造）】\n{local_kb}\n"
        if travel_guidance:
            extra += f"\n【出行对话指引】\n{travel_guidance}\n"
        if mcp_context:
            extra += f"\n【时间/地图/联网等参考】\n{mcp_context}\n"
        tail = """
你正在「对话」模式：可代写帖文、站点帮助、出行规划。回复可直接使用。"""
        if travel_guidance and "Markdown 表格" in travel_guidance:
            tail += " 出行规划请用 Markdown 表格输出阶段路线，并单独写目的地天气。"
        else:
            tail += " 普通对话用自然短文即可。"
        return base + tail + extra
    return base + """
basic 用户若要求重度能力，礼貌说明 VIP 功能。"""


def _skill_system(
    skill: str,
    tier: str,
    appearance: str,
    *,
    local_kb: str = "",
    mcp_context: str = "",
    travel_guidance: str = "",
    related_articles_prompt: str = "",
) -> str:
    base = f"""你是论坛网站的看板娘助手，用自然、简短的中文回复。

当前用户档位 tier={tier}（basic=普通用户, vip=会员/管理员体验档）。
用户当前选择的 Live2D 模型代码 mascot_model={appearance}（仅作人设上下文）。
当前功能 skill={skill}（writing=写作代笔, help=站点帮助）。

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
        if related_articles_prompt:
            extra += f"\n【相关站内帖子（系统会在消息下方展示链接，可引用标题）】\n{related_articles_prompt}\n"
        if local_kb:
            extra += f"\n【本站知识库（优先使用，无需编造）】\n{local_kb}\n"
        if travel_guidance:
            extra += f"\n【出行对话指引】\n{travel_guidance}\n"
        if mcp_context:
            extra += f"\n【时间/地图/联网等参考】\n{mcp_context}\n"
        tail = """
你正在「对话」模式：可代写帖文、站点帮助、出行规划。"""
        if travel_guidance and "Markdown 表格" in travel_guidance:
            tail += " 出行规划请在 reply 中用 Markdown 表格写阶段路线，并写天气小节。"
        return base + tail + extra
    return base + """
basic 用户若要求重度能力，礼貌说明 VIP 功能；live2d.suggested_appearance 仅 legacy: standard|keyboard|gamepad 或 null。"""


def node_assess(state: MascotState) -> MascotState:
    skill = _effective_skill(state)
    message = (state.get("message") or "").strip()
    history = state.get("history") or []
    client_dt = str(state.get("client_datetime") or "").strip()

    bundle = prepare_mascot_mcp_bundle(
        message=message,
        history=history,
        skill=skill,
        client_datetime=client_dt or None,
    )

    related: list[dict[str, Any]] = []
    related_prompt = ""
    if skill == "writing" and len(message) >= 2:
        try:
            related = fetch_related_articles(message)
            related_prompt = format_related_for_prompt(related)
        except Exception:
            logger.exception("看板娘帖子向量检索失败")

    out: MascotState = {
        "need_mcp_search": bool(bundle.get("need_mcp_search")),
        "mcp_query": bundle.get("mcp_query") or "",
        "mcp_context": bundle.get("mcp_context") or "",
        "local_kb_snippet": bundle.get("local_kb_snippet") or "",
        "datetime_context": bundle.get("datetime_context") or "",
        "travel_guidance": bundle.get("travel_guidance") or "",
        "travel_phase": bundle.get("travel_phase") or "none",
        "related_articles": related,
        "related_articles_prompt": related_prompt,
        "mcp_used": bool(bundle.get("mcp_used")),
    }
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
    prev = (state.get("mcp_context") or "").strip()
    if ctx and prev:
        merged = f"{prev}\n\n【联网检索参考】\n{ctx}"
    elif ctx:
        merged = f"【联网检索参考】\n{ctx}"
    else:
        merged = prev
    return {"mcp_context": merged}


def node_agent(state: MascotState) -> MascotState:
    tier = (state.get("tier") or "basic").lower()
    appearance = (state.get("appearance") or "snow_miku").lower()
    skill = _effective_skill(state)
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
        travel_guidance=state.get("travel_guidance") or "",
        related_articles_prompt=state.get("related_articles_prompt") or "",
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
    g.add_node("route_skill", node_route_skill)
    g.add_node("assess", node_assess)
    g.add_node("tavily_search", node_tavily_search)
    g.add_node("agent", node_agent)
    g.add_edge(START, "route_skill")
    g.add_edge("route_skill", "assess")
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
    skill: str = "chat",
    vip_tier: int = 0,
    client_datetime: str = "",
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
            "skill": skill or "chat",
            "llm_route": llm_provider or "",
            "history": history or [],
            "client_datetime": client_datetime or "",
        }
    )
    # route_skill / assess 在图内执行；invoke 后补全 routed_skill 供调试
    mcp_used = bool(out.get("need_mcp_search")) or bool(out.get("mcp_used"))
    return {
        "reply": out.get("reply", ""),
        "live2d": out.get("live2d") or {},
        "suggested_appearance": out.get("suggested_appearance"),
        "usage": out.get("usage") or {},
        "mcp_used": mcp_used,
    }
