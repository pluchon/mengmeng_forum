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


def _parse_json(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def _llm_route(message: str, history: list[dict[str, str]]) -> str:
    ds = settings.dashscope
    model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
    hist_txt = ""
    for item in (history or [])[-4:]:
        role = item.get("role", "")
        content = (item.get("content") or "")[:300]
        hist_txt += f"{role}: {content}\n"
    sys = (
        "你是论坛看板娘的意图路由器。判断用户本轮更需要哪种能力：\n"
        "- help：询问本站功能、规则、积分、VIP、发帖流程、消息、审核等使用问题；"
        "回答应简短条目化，不代写长文，不联网。\n"
        "- writing：代写/润色论坛帖文、查外部事实、旅行闲聊与路线、需结合联网或地图的场景。\n"
        '只输出 JSON：{"intent":"help|writing"}'
    )
    user = f"近期对话：\n{hist_txt}\n本轮用户：{message[:800]}"
    try:
        raw, _ = dashscope_chat_completion(
            model,
            [{"role": "system", "content": sys}, {"role": "user", "content": user}],
            temperature=0.05,
        )
    except Exception:
        logger.exception("看板娘意图路由 LLM 失败")
        return "writing"
    data = _parse_json(raw)
    if not isinstance(data, dict):
        return "writing"
    intent = str(data.get("intent") or "").strip().lower()
    return "help" if intent == "help" else "writing"


def route_mascot_skill(message: str, history: list[dict[str, str]] | None) -> str:
    """由 Flash 模型返回 writing 或 help。"""
    return _llm_route(message, history or [])
