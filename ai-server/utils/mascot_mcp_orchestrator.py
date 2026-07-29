"""
看板娘 MCP 编排：联网搜索、百度地图出行、当前时间.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings
from mcp.registry import invoke_tool
from utils.datetime_context import build_datetime_context
from utils.mascot_travel import merge_travel_plans, rule_travel_plan
from utils.mcp_routing import (
    assess_mcp_for_writing,
    is_explicit_web_image_request,
    is_tavily_search_enabled,
)

logger = logging.getLogger(__name__)

_AFFIRM_RE = re.compile(r"^(是的?|对|嗯|好|可以|想去|要去|走|行|没问题|ok|yes)[\s!！。~]*$", re.I)
_TRAVEL_TOPIC_RE = re.compile(r"雪山|川西|西藏|新疆|云南|旅行|旅游|自驾|路线|出发|想去|行程|攻略", re.I)
_LOCAL_WEATHER_RE = re.compile(r"天气|温度|下雨|降雨|带伞|穿什么|冷不冷|热不热|空气", re.I)
_IMAGE_REQUEST_TERMS_RE = re.compile(
    r"我(?:是)?说|请|帮我|给我|把|叫你|在|从|互联网上?|网络上?|联网|检索到的?|"
    r"搜索到的?|搜索|搜|检索|抓取|展示|显示|看看|图片|图集|配图|照片|一下",
    re.I,
)


def _parse_json(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def _baidu_enabled() -> bool:
    mcp = settings.raw.get("mcp", {}) or {}
    if not mcp.get("enabled", True):
        return False
    bm = settings.baidu_map
    if not bm.get("enabled", True):
        return False
    from clients.baidu_map_client import is_configured

    return is_configured()


def _llm_travel_tool_plan(message: str, history: list[dict[str, str]], datetime_ctx: str) -> dict[str, Any]:
    """决定是否调用百度地图工具及参数."""
    ds = settings.dashscope
    model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
    hist_txt = ""
    for item in (history or [])[-6:]:
        role = item.get("role", "")
        content = (item.get("content") or "")[:400]
        hist_txt += f"{role}: {content}\n"
    sys = (
        "你是看板娘出行规划工具调度器。根据对话判断：\n"
        "1) phase=none：普通闲聊，不需要地图。\n"
        "2) phase=inspire：用户赞叹某地风景，助手已/将邀请出行，本轮只需情感回应，不调用地图。\n"
        "3) phase=collect：用户愿意出行但缺少出发地/目的地/出行方式/人数/日期之一，不调用地图，"
        "在 missing_fields 列出缺什么（必须包含：出发地、目的地、出行人数、出行方式是否驾车、计划出发日期）。\n"
        "4) phase=plan：信息足够，可规划路线，在 tool_calls 中给出调用列表。\n"
        "可用工具：map_geocode, map_search_places, map_directions（mode: driving/walking/riding/transit）, "
        "map_weather（location=目的地城市或景区名）。\n"
        "只输出 JSON："
        '{"phase":"none|inspire|collect|plan","missing_fields":[],"tool_calls":[{"tool":"map_directions",'
        '"arguments":{"origin":"","destination":"","mode":"driving"}},{"tool":"map_weather",'
        '"arguments":{"location":""}}],"destination_hint":"川西"}'
    )
    user = f"{datetime_ctx}\n历史：\n{hist_txt}\n本轮用户：{message[:800]}"
    try:
        raw, _ = dashscope_chat_completion(
            model,
            [{"role": "system", "content": sys}, {"role": "user", "content": user}],
            temperature=0.1,
        )
    except Exception:
        logger.exception("出行工具调度 LLM 失败")
        return {"phase": "none"}
    data = _parse_json(raw)
    return data if isinstance(data, dict) else {"phase": "none"}


def _run_tool_calls(tool_calls: list[Any], *, client_datetime: str) -> str:
    blocks: list[str] = []
    for item in tool_calls:
        if not isinstance(item, dict):
            continue
        name = str(item.get("tool") or "").strip()
        args = item.get("arguments")
        if not isinstance(args, dict):
            args = {}
        if name == "get_current_datetime":
            args = {"client_datetime": client_datetime}
        try:
            out = invoke_tool(name, args)
        except Exception as e:
            logger.exception("工具 %s 失败", name)
            out = f"{name} 调用失败: {e}"
        blocks.append(f"【{name}】\n{out}")
    return "\n\n".join(blocks)


def _plan_reply_guidance() -> str:
    return (
        "已获取百度地图路线与天气数据。回复时必须：\n"
        "1) 用 Markdown 表格输出行程阶段（列：阶段、路段/地点、预计用时、备注）；\n"
        "2) 单独一小节写「目的地天气」，引用 map_weather 结果；\n"
        "3) 不要编造未提供的里程；提醒以实际路况为准。\n"
        "4) 相关站内帖子链接由系统在消息下方自动展示，正文中可提及标题但不要伪造链接。"
    )


def _web_image_search_query(message: str, history: list[dict[str, str]] | None) -> str:
    """优先保留本轮主体；纯指令则继承最近一轮用户主题。"""
    message = (message or "").strip()
    subject = _IMAGE_REQUEST_TERMS_RE.sub("", message)
    subject = re.sub(r"[，。！？、\s]+", "", subject)
    if len(subject) >= 3:
        return subject[:300]
    for item in reversed(history or []):
        if str(item.get("role") or "").lower() != "user":
            continue
        previous = str(item.get("content") or "").strip()
        if previous:
            return previous[:300]
    return message[:300]


def prepare_mascot_mcp_bundle(
    *,
    message: str,
    history: list[dict[str, str]] | None,
    skill: str,
    client_datetime: str | None,
    client_location: str | None = None,
) -> dict[str, Any]:
    """
    返回 datetime_context, local_kb_snippet, mcp_context、联网搜索及图集请求信息。
    """
    datetime_ctx = build_datetime_context(client_datetime)
    out: dict[str, Any] = {
        "datetime_context": datetime_ctx,
        "local_kb_snippet": "",
        "mcp_context": "",
        "need_mcp_search": False,
        "need_search_images": False,
        "mcp_query": "",
        "mcp_used": False,
        "travel_phase": "none",
        "travel_guidance": "",
    }
    ctx_parts: list[str] = [datetime_ctx]

    if skill == "writing":
        explicit_image_request = is_explicit_web_image_request(message)
        if explicit_image_request:
            need = is_tavily_search_enabled()
            query = _web_image_search_query(message, history)
            snippet = ""
            out["need_search_images"] = need
        else:
            need, query, snippet = assess_mcp_for_writing(message)
        out["local_kb_snippet"] = snippet
        out["need_mcp_search"] = need
        out["mcp_query"] = query

    location = str(client_location or "").strip()[:80]
    if skill == "writing" and location and _LOCAL_WEATHER_RE.search(message) and _baidu_enabled():
        try:
            weather_context = invoke_tool("map_weather", {"location": location})
            if weather_context:
                ctx_parts.append(f"【用户所在城市天气】\n{weather_context}")
                out["mcp_used"] = True
        except Exception:
            logger.exception("用户所在城市天气查询失败")

    if skill != "writing" or not _baidu_enabled():
        out["mcp_context"] = "\n".join(ctx_parts)
        return out

    rule_plan = rule_travel_plan(message, history or [])
    llm_plan = _llm_travel_tool_plan(message, history or [], datetime_ctx)
    plan = merge_travel_plans(rule_plan, llm_plan)
    phase = str(plan.get("phase") or "none")
    out["travel_phase"] = phase

    if phase == "collect":
        missing = plan.get("missing_fields") or []
        if isinstance(missing, list) and missing:
            fields = "、".join(str(x) for x in missing[:6])
            out["travel_guidance"] = (
                f"用户有意出行，但尚缺：{fields}。请友好、逐项追问（例如：从哪出发？几个人？"
                f"打算开车还是公共交通？计划哪天出发？），不要编造路线。"
            )
        else:
            out["travel_guidance"] = (
                "用户有意出行，请主动询问：出发地、目的地、出行人数、是否驾车（或公交/步行/骑行）、"
                "计划出发日期。一次可问 2～3 项，语气亲切。信息不足前不要调用地图。"
            )
        ctx_parts.append(out["travel_guidance"])

    elif phase == "inspire":
        dest = str(plan.get("destination_hint") or "").strip()
        hint = (
            "用户表达对风景/旅行的喜爱。请先结合相关帖子内容回答，"
            "结尾自然追问是否想一起去（如「您是不是特别想去呢？」）。"
        )
        if dest:
            hint += f" 目的地线索：{dest}。"
        hint += " 不要在本轮调用地图或编造路线。"
        out["travel_guidance"] = hint
        ctx_parts.append(hint)

    elif phase == "plan":
        tool_calls = plan.get("tool_calls")
        if isinstance(tool_calls, list) and tool_calls:
            map_ctx = _run_tool_calls(tool_calls, client_datetime=client_datetime or "")
            if map_ctx:
                ctx_parts.append(f"【百度地图工具结果】\n{map_ctx}")
                out["mcp_used"] = True
        out["travel_guidance"] = _plan_reply_guidance()

    # 规则补强：用户简短肯定 + 上文有旅行话题
    if phase == "none" and _AFFIRM_RE.match(message.strip()) and history:
        prev = " ".join(
            (h.get("content") or "") for h in history[-4:] if h.get("role") == "assistant"
        )
        if _TRAVEL_TOPIC_RE.search(prev):
            out["travel_guidance"] = (
                "用户可能同意出行。请结合上文目的地，主动询问：从哪出发、几个人、"
                "是否开车/公交、计划哪天出发；信息不足前不要编造路线。"
            )
            out["travel_phase"] = "collect"
            ctx_parts.append(out["travel_guidance"])

    out["mcp_context"] = "\n\n".join(p for p in ctx_parts if p)
    return out
