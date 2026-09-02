"""
看板娘 skill=chat 时，将用户意图路由为 writing（代笔/联网/MCP）或 help（站点帮助）。
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings
logger = logging.getLogger(__name__)

# 只要一个 {"intent":"..."}，给它封顶，异常时不至于一路生成到默认上限
_ROUTE_MAX_TOKENS = 64


def _parse_json(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def _llm_route(message: str, history: list[dict[str, str]]) -> tuple[str, dict[str, Any]]:
    ds = settings.dashscope
    model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.7-flash")
    hist_txt = ""
    for item in (history or [])[-4:]:
        role = item.get("role", "")
        content = (item.get("content") or "")[:300]
        hist_txt += f"{role}: {content}\n"
    sys = (
        "你是论坛看板娘的意图路由器。判断用户本轮更需要哪种能力。"
        "禁止用关键词字面匹配，要理解整轮语义（含最近对话）。\n"
        "- help：仅当用户在问本站「怎么用/规则是什么」——功能入口、版规、积分、VIP、发帖流程、"
        "消息、审核、账号设置等操作或规则说明；回答应短，不代写长文，不联网，不负责找帖内容。\n"
        "- writing：闲聊陪聊、想看/找/推荐站内帖子、代写润色、查外部事实、旅行路线、"
        "需要联网或生图等相关场景。找帖看帖属于 writing，不是 help。\n"
        '只输出 JSON：{"intent":"help|writing"}'
    )
    user = f"近期对话：\n{hist_txt}\n本轮用户：{message[:800]}"
    try:
        # 这一次调用的用量原来被直接丢掉，等于每轮对话都少算一次 flash
        raw, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": sys}, {"role": "user", "content": user}],
            temperature=0.05,
            max_tokens=_ROUTE_MAX_TOKENS,
        )
    except Exception:
        logger.exception("看板娘意图路由 LLM 失败")
        return "writing", {}
    data = _parse_json(raw)
    if not isinstance(data, dict):
        return "writing", usage
    intent = str(data.get("intent") or "").strip().lower()
    return ("help" if intent == "help" else "writing"), usage


def route_mascot_skill(
    message: str,
    history: list[dict[str, str]] | None,
) -> tuple[str, dict[str, Any]]:
    """由 Flash 模型返回 (writing|help, 本次用量)。"""
    return _llm_route(message, history or [])
