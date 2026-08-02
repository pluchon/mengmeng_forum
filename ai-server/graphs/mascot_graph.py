"""
看板娘对话 — LangGraph 编排:

  START -> route_skill -> supervisor -> assess -> [tavily_search?] -> agent -> END

- skill=chat：自动路由为 writing | help
- writing：本地站点文档可答则不搜；否则按需 MCP(Tavily) / 地图
- help：仅用站点帮助，不 MCP
"""
from __future__ import annotations

import json
import logging
import re
import time
from collections.abc import Iterator
from typing import Any, Literal, TypedDict

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from clients.dashscope_chat_client import (
    dashscope_chat_completion,
    dashscope_stream_text,
    lc_messages_to_openai,
)
from config import settings
from mcp.registry import invoke_tool
from utils.mascot_article_rag import fetch_related_articles
from utils.mcp_routing import local_kb_covers_writing
from utils.mascot_skill_router import route_mascot_skill
from utils.site_help import get_site_help_snippet

logger = logging.getLogger(__name__)

_AGENT_TOOL_NAMES = {"rag", "web_search", "image_generation"}
_MAX_TOOL_ROUNDS = 2


class MascotState(TypedDict, total=False):
    message: str
    session_id: str
    appearance: str
    tier: str
    vip_tier: int
    skill: str
    routed_skill: str
    action: str
    image_prompt: str
    supervisor_usage: dict[str, Any]
    complexity: str
    related_search_offer: bool
    related_search_query: str
    need_search_images: bool
    llm_route: str
    history: list[dict[str, str]]
    need_mcp_search: bool
    mcp_query: str
    mcp_context: str
    search_image_gallery: list[dict[str, str]]
    local_kb_snippet: str
    client_datetime: str
    client_location: str
    datetime_context: str
    mcp_used: bool
    reply: str
    live2d: dict[str, Any]
    suggested_appearance: str | None
    usage: dict[str, Any]
    planned_tools: list[dict[str, Any]]
    completed_tools: list[str]
    tool_round: int


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
    if skill == "help" or vip_tier < 1 or raw != "qwen-deep":
        return "qwen-flash"
    return "qwen-deep"


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


def node_supervisor(state: MascotState) -> MascotState:
    """初始化自主工具循环；工具选择由后续 Planner 模型决定。"""
    return {
        "action": "CHAT",
        "image_prompt": "",
        "complexity": "SIMPLE",
        "related_search_offer": False,
        "related_search_query": "",
        "need_search_images": False,
        "planned_tools": [],
        "completed_tools": [],
        "tool_round": 0,
        "supervisor_usage": {},
    }


def _route_after_supervisor(state: MascotState) -> Literal["tool_planner"]:
    return "tool_planner"


def _plan_tool_calls(state: MascotState) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """让模型选择本轮可组合的只读工具，服务端仍执行与约束每一个调用。"""
    if _effective_skill(state) == "help" or int(state.get("tool_round") or 0) >= _MAX_TOOL_ROUNDS:
        return [], {}
    message = (state.get("message") or "").strip()
    if not message:
        return [], {}
    completed = {str(name) for name in state.get("completed_tools") or []}
    prior = (state.get("mcp_context") or "")[-1800:]
    system = """你是论坛看板娘的工具规划器。你可以在一轮中组合多个工具，并会在每轮工具结果后再次决定是否继续。
只从以下工具中选择直接回答用户所必需的工具：
- rag：检索公开的站内知识与帖子索引；
- web_search：查询会变化的外部事实或用户明确要求联网的信息；
- image_generation：按用户明确要求新生成一张图片。
普通聊天、改写、已有上下文可回答的问题不要调用工具。不能调用已经完成的工具；普通用户不能使用 image_generation。
图片检索和图片生成不同：只有“新生成一张图”才选 image_generation。工具失败不应阻断后续回答。
只输出 JSON：{"tool_calls":[{"name":"rag|web_search|image_generation","query":"检索词或空","include_images":false,"prompt":"仅生图时填写"}],"complexity":"SIMPLE|COMPLEX"}。最多三个 tool_calls。"""
    user = (
        f"用户档位：{'vip' if _vip_tier_num(state) >= 1 else 'basic'}\n"
        f"已完成工具：{','.join(sorted(completed)) or '无'}\n"
        f"已有工具结果：{prior or '无'}\n"
        f"用户请求：{message[:2000]}"
    )
    model = str(settings.dashscope.get("model_text_flash") or settings.dashscope.get("model_text") or "qwen3.6-flash")
    try:
        raw, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": system}, {"role": "user", "content": user}],
            temperature=0.0,
        )
    except Exception:
        logger.exception("看板娘工具规划失败")
        return [], {"model_code": model, "estimated": True}
    data = _parse_json_object(raw)
    if not isinstance(data, dict):
        return [], usage
    calls: list[dict[str, Any]] = []
    raw_calls = data.get("tool_calls")
    if not isinstance(raw_calls, list):
        raw_calls = []
    for raw_call in raw_calls:
        if not isinstance(raw_call, dict):
            continue
        name = str(raw_call.get("name") or "").strip().lower()
        if name not in _AGENT_TOOL_NAMES or name in completed or any(x["name"] == name for x in calls):
            continue
        if name == "image_generation" and _vip_tier_num(state) < 1:
            continue
        query = str(raw_call.get("query") or message).strip()[:500]
        prompt = str(raw_call.get("prompt") or "").strip()[:1600]
        if name == "image_generation" and not prompt:
            prompt = message[:1600]
        calls.append({
            "name": name,
            "query": query,
            "prompt": prompt,
            "include_images": bool(raw_call.get("include_images")),
        })
        if len(calls) >= 3:
            break
    return calls, usage


def node_tool_planner(state: MascotState) -> MascotState:
    calls, usage = _plan_tool_calls(state)
    complexity = "SIMPLE"
    if calls and any(call["name"] in {"rag", "web_search"} for call in calls):
        complexity = "COMPLEX"
    return {
        "planned_tools": calls,
        "complexity": complexity,
        "supervisor_usage": _merge_usage(usage, state.get("supervisor_usage")),
    }


def node_image_action(state: MascotState) -> MascotState:
    """在允许工具完成后再声明生图委派，保留检索/RAG 等组合上下文。"""
    prompt = (state.get("image_prompt") or state.get("message") or "").strip()
    context = "\n\n".join(part for part in (
        state.get("local_kb_snippet") or "",
        state.get("mcp_context") or "",
    ) if part).strip()
    if context:
        prompt = f"{prompt}\n\n参考素材：\n{context[:2400]}"
    return {"action": "IMAGE", "image_prompt": prompt[:4000]}


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
    from clients.usage_util import attach_latency

    t0 = time.perf_counter()
    ds = settings.dashscope
    openai_msgs = _lc_to_openai_messages(msgs)

    if route == "qwen-flash":
        model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
        text, usage = dashscope_chat_completion(model, lc_messages_to_openai(msgs), temperature=0.6)
        return text, attach_latency(usage, t0)

    if route == "qwen-deep":
        model = str(ds.get("model_text_deep") or "qwen3.7-max")
        text, usage = dashscope_chat_completion(model, lc_messages_to_openai(msgs), temperature=0.55)
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


def _invoke_mascot_llm_stream(
    route: str,
    msgs: list[Any],
) -> Iterator[tuple[str, dict[str, Any] | None]]:
    """yield (text_chunk, usage_or_none)；usage 仅在最后一次非 None。"""
    ds = settings.dashscope
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
    client_location: str = "",
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
        "client_location": client_location or "",
    }
    state.update(node_route_skill(state))
    state.update(node_supervisor(state))
    for _ in range(_MAX_TOOL_ROUNDS):
        state.update(node_tool_planner(state))
        if not state.get("planned_tools"):
            break
        state.update(node_execute_tools(state))
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
    client_location: str = "",
):
    """yield ('text', str) 或 ('usage', dict)。"""
    yield ("status", "preparing")
    ctx: dict[str, Any] = {}
    for status, current_state in _stream_mascot_context(
        message=message,
        session_id=session_id,
        appearance=appearance,
        tier=tier,
        history=history,
        llm_provider=llm_provider,
        skill=skill,
        vip_tier=vip_tier,
        client_datetime=client_datetime,
        client_location=client_location,
    ):
        ctx = current_state
        yield ("status", status)
    if ctx.get("action") == "IMAGE":
        yield ("meta", {"action": "IMAGE", "imagePrompt": ctx.get("image_prompt") or ""})
    if ctx.get("related_search_offer") and ctx.get("related_search_query"):
        yield ("meta", {
            "relatedSearchOffer": True,
            "relatedSearchQuery": ctx.get("related_search_query") or "",
            "complexity": ctx.get("complexity") or "SIMPLE",
        })
    search_gallery = ctx.get("search_image_gallery") or []
    if search_gallery:
        yield ("meta", {"searchImageGallery": search_gallery})
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
    yield ("usage", _merge_usage(attach_latency(usage, t0), ctx.get("supervisor_usage")))


def _stream_mascot_context(
    *,
    message: str,
    session_id: str,
    appearance: str,
    tier: str,
    history: list[dict[str, str]] | None,
    llm_provider: str,
    skill: str,
    vip_tier: int,
    client_datetime: str,
    client_location: str,
) -> Iterator[tuple[str, dict[str, Any]]]:
    """通过 LangGraph 逐节点产出看板娘准备阶段的真实状态。"""
    global _STREAM_PREPARE_GRAPH
    if _STREAM_PREPARE_GRAPH is None:
        _STREAM_PREPARE_GRAPH = build_mascot_prepare_graph()
    state: dict[str, Any] = {
        "message": message,
        "session_id": session_id or "",
        "appearance": appearance or "snow_miku",
        "tier": tier or "basic",
        "vip_tier": vip_tier,
        "skill": skill or "chat",
        "llm_route": llm_provider or "",
        "history": history or [],
        "client_datetime": client_datetime or "",
        "client_location": client_location or "",
    }
    yield "routing", state
    for update in _STREAM_PREPARE_GRAPH.stream(state, stream_mode="updates"):
        for node_name, node_state in update.items():
            if isinstance(node_state, dict):
                state.update(node_state)
            if node_name == "route_skill":
                yield "supervising", state
            elif node_name == "supervisor":
                yield "planning", state
            elif node_name == "tool_planner":
                yield "using_tools" if state.get("planned_tools") else "composing", state
            elif node_name == "execute_tools":
                yield "drawing" if state.get("action") == "IMAGE" else "composing", state
            elif node_name == "tavily_search":
                yield "composing", state


def _skill_system_stream(
    skill: str,
    tier: str,
    appearance: str,
    *,
    local_kb: str = "",
    mcp_context: str = "",
) -> str:
    base = f"""你是论坛网站的看板娘助手，用自然、简短、有聊天感的中文回复。

当前用户档位 tier={tier}（basic=普通用户, vip=会员/管理员体验档）。
用户当前选择的 Live2D 模型代码 mascot_model={appearance}（仅作人设上下文）。
当前功能 skill={skill}（writing=写作代笔, help=站点帮助）。

请直接输出回复正文，不要使用 JSON 或代码围栏。
先回应用户真正关心的内容，不要描述自己的工作流程。
禁止使用“我来帮你整理帖子”“我来规划出行”“我来整理行程”等机械开场，也不要提及内部工具或节点。
可以按语境自然使用少量“嗯、呀、啦、哦”等语气词，但不要堆叠 emoji、颜文字或夸张卖萌。
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
            extra += f"\n【时间/地图/联网等参考】\n{mcp_context}\n"
        tail = """
你正在「对话」模式：可协助写帖、解答站点问题、聊聊地点和天气等生活话题。回复直接给用户可用的内容。
若普通用户明确要求生图，请简短说明该能力仅向会员开放，不要声称已经生成图片。
若系统已展示联网配图，正文无需再插入多张图片；不要说自己无法联网或无法展示图片。
不要主动宣称已经检索过部落内容；是否检索由用户确认后由系统单独处理。
若有用户所在城市的生活参考，只在当前外出话题确实相关时自然带一句；不要提及 IP、定位过程或精确地址。普通对话用自然短文即可。"""
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
) -> str:
    base = f"""你是论坛网站的看板娘助手，用自然、简短、有聊天感的中文回复。

当前用户档位 tier={tier}（basic=普通用户, vip=会员/管理员体验档）。
用户当前选择的 Live2D 模型代码 mascot_model={appearance}（仅作人设上下文）。
当前功能 skill={skill}（writing=写作代笔, help=站点帮助）。

**必须只输出一段合法 JSON**，不要 Markdown 代码围栏:
{{"reply":"...","live2d":{{}},"suggested_appearance":null}}
reply 先回应用户真正关心的内容，不要描述自己的工作流程。
禁止使用“我来帮你整理帖子”“我来规划出行”“我来整理行程”等机械开场，也不要提及内部工具或节点。
可以按语境自然使用少量“嗯、呀、啦、哦”等语气词，但不要堆叠 emoji、颜文字或夸张卖萌。
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
            extra += f"\n【时间/地图/联网等参考】\n{mcp_context}\n"
        tail = """
你正在「对话」模式：可协助写帖、解答站点问题、聊聊地点和天气等生活话题。直接给用户可用的内容。
若有用户所在城市的生活参考，只在当前外出话题确实相关时自然带一句；不要提及 IP、定位过程或精确地址。"""
        return base + tail + extra
    return base + """
basic 用户若要求重度能力，礼貌说明 VIP 功能；live2d.suggested_appearance 仅 legacy: standard|keyboard|gamepad 或 null。"""


def _run_rag_tool(query: str) -> tuple[str, str]:
    """RAG 只注入公开站点知识；帖子索引仅作为受 Java 复查的线索。"""
    covered, snippet = local_kb_covers_writing(query)
    if covered and snippet:
        return snippet[:2500], "站内知识库"
    try:
        related = fetch_related_articles(query)
    except Exception:
        logger.exception("看板娘 RAG 检索失败")
        return "", ""
    if not related:
        return "", ""
    ids = ", ".join(str(item.get("articleId")) for item in related if item.get("articleId") is not None)
    # 不向模型泄露未经 Java 可见性复查的帖子正文或标题。
    return f"【站内帖子检索线索】命中公开索引候选 #{ids}；仅可作为相关帖子推荐线索，不能据此断言帖子内容。", "站内帖子索引"


def node_execute_tools(state: MascotState) -> MascotState:
    """执行模型选出的工具；单个失败被隔离，其他工具与最终回答继续。"""
    calls = state.get("planned_tools") or []
    context_parts = [part for part in (state.get("mcp_context") or "").split("\n\n") if part.strip()]
    local_kb = state.get("local_kb_snippet") or ""
    gallery = list(state.get("search_image_gallery") or [])
    completed = list(state.get("completed_tools") or [])
    image_prompt = state.get("image_prompt") or ""
    action = state.get("action") or "CHAT"
    need_search_images = False
    for call in calls:
        if not isinstance(call, dict):
            continue
        name = str(call.get("name") or "")
        query = str(call.get("query") or state.get("message") or "").strip()
        try:
            if name == "rag":
                rag_context, source = _run_rag_tool(query)
                if rag_context:
                    local_kb = "\n\n".join(part for part in (local_kb, rag_context) if part)[:4000]
                    context_parts.append(f"【{source}】\n{rag_context}")
            elif name == "web_search":
                web_state: MascotState = dict(state)
                web_state["mcp_query"] = query
                web_state["need_search_images"] = bool(call.get("include_images"))
                result = node_tavily_search(web_state)
                web_context = str(result.get("mcp_context") or "").strip()
                if web_context:
                    context_parts.append(web_context)
                found_gallery = result.get("search_image_gallery") or []
                if isinstance(found_gallery, list):
                    gallery = found_gallery[:5]
                need_search_images = bool(call.get("include_images"))
            elif name == "image_generation":
                image_state: MascotState = dict(state)
                image_state["image_prompt"] = str(call.get("prompt") or query).strip()
                image_state["local_kb_snippet"] = local_kb
                image_state["mcp_context"] = "\n\n".join(context_parts)[-4000:]
                image_prompt = str(node_image_action(image_state).get("image_prompt") or "")
                action = "IMAGE"
            else:
                continue
            completed.append(name)
        except Exception:
            logger.exception("看板娘工具执行失败 tool=%s", name)
    return {
        "mcp_context": "\n\n".join(context_parts)[-6000:],
        "local_kb_snippet": local_kb[:4000],
        "search_image_gallery": gallery[:5],
        "need_search_images": need_search_images,
        "mcp_used": bool(completed),
        "completed_tools": completed,
        "tool_round": int(state.get("tool_round") or 0) + 1,
        "action": action,
        "image_prompt": image_prompt[:4000],
    }


def _route_after_execute_tools(state: MascotState) -> Literal["tool_planner", "agent"]:
    if state.get("planned_tools") and int(state.get("tool_round") or 0) < _MAX_TOOL_ROUNDS:
        return "tool_planner"
    return "agent"


def node_tavily_search(state: MascotState) -> MascotState:
    query = (state.get("mcp_query") or state.get("message") or "").strip()
    ctx = ""
    search_image_gallery: list[dict[str, str]] = []
    try:
        from clients.tavily_client import TavilySearchClient
        from config import settings as _settings

        cfg = _settings.tavily
        client = TavilySearchClient()
        if client.is_configured():
            ctx, search_image_gallery = client.search_for_chat(
                query,
                max_results=int(cfg.get("max_results", 5)),
                search_depth=str(cfg.get("search_depth", "basic")),
                include_images=bool(state.get("need_search_images")),
                prefer_encyclopedia=bool(state.get("need_search_images")),
            )
        else:
            ctx = invoke_tool("tavily_search", {"query": query})
    except Exception:
        logger.exception("MCP tavily_search 失败")
        try:
            ctx = invoke_tool("tavily_search", {"query": query})
        except Exception:
            ctx = ""
    if search_image_gallery:
        ctx = f"{ctx}\n\n【联网配图】已整理 {len(search_image_gallery)} 张相关图片，系统会在本条消息的图集入口展示。"
    elif state.get("need_search_images"):
        ctx = f"{ctx}\n\n【联网配图】本次没有检索到适合展示的相关图片。"
    prev = (state.get("mcp_context") or "").strip()
    if ctx and prev:
        merged = f"{prev}\n\n【联网检索参考】\n{ctx}"
    elif ctx:
        merged = f"【联网检索参考】\n{ctx}"
    else:
        merged = prev
    return {"mcp_context": merged, "search_image_gallery": search_image_gallery}


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
            "usage": _merge_usage(usage, state.get("supervisor_usage")),
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
        "usage": _merge_usage(usage, state.get("supervisor_usage")),
    }


def _merge_usage(main: dict[str, Any], supervisor: Any) -> dict[str, Any]:
    """将 Supervisor 的轻量判断计入本次看板娘用量。"""
    merged = dict(main or {})
    if not isinstance(supervisor, dict):
        return merged
    for key in ("input_tokens", "output_tokens", "latency_ms"):
        merged[key] = int(merged.get(key) or 0) + int(supervisor.get(key) or 0)
    merged["estimated"] = bool(merged.get("estimated")) or bool(supervisor.get("estimated"))
    return merged


def build_mascot_graph() -> Any:
    g = StateGraph(MascotState)
    g.add_node("route_skill", node_route_skill)
    g.add_node("supervisor", node_supervisor)
    g.add_node("tool_planner", node_tool_planner)
    g.add_node("execute_tools", node_execute_tools)
    g.add_node("tavily_search", node_tavily_search)
    g.add_node("agent", node_agent)
    g.add_edge(START, "route_skill")
    g.add_edge("route_skill", "supervisor")
    g.add_conditional_edges("supervisor", _route_after_supervisor, {"tool_planner": "tool_planner"})
    g.add_edge("tool_planner", "execute_tools")
    g.add_conditional_edges("execute_tools", _route_after_execute_tools, {"tool_planner": "tool_planner", "agent": "agent"})
    g.add_edge("agent", END)
    return g.compile()


def build_mascot_prepare_graph() -> Any:
    """流式回复的准备子图：复用节点，但不提前执行最终文本模型。"""
    g = StateGraph(MascotState)
    g.add_node("route_skill", node_route_skill)
    g.add_node("supervisor", node_supervisor)
    g.add_node("tool_planner", node_tool_planner)
    g.add_node("execute_tools", node_execute_tools)
    g.add_edge(START, "route_skill")
    g.add_edge("route_skill", "supervisor")
    g.add_conditional_edges("supervisor", _route_after_supervisor, {"tool_planner": "tool_planner"})
    g.add_edge("tool_planner", "execute_tools")
    g.add_conditional_edges("execute_tools", _route_after_execute_tools, {"tool_planner": "tool_planner", "agent": END})
    return g.compile()


_GRAPH = None
_STREAM_PREPARE_GRAPH = None


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
    client_location: str = "",
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
            "client_location": client_location or "",
        }
    )
    # route_skill / assess 在图内执行；invoke 后补全 routed_skill 供调试
    mcp_used = bool(out.get("mcp_used")) or bool(out.get("completed_tools"))
    return {
        "reply": out.get("reply", ""),
        "live2d": out.get("live2d") or {},
        "suggested_appearance": out.get("suggested_appearance"),
        "usage": out.get("usage") or out.get("supervisor_usage") or {},
        "mcp_used": mcp_used,
        "action": out.get("action") or "CHAT",
        "image_prompt": out.get("image_prompt") or "",
        "complexity": out.get("complexity") or "SIMPLE",
        "related_search_offer": bool(out.get("related_search_offer")),
        "related_search_query": out.get("related_search_query") or "",
        "search_image_gallery": out.get("search_image_gallery") or [],
    }
