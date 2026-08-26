"""看板娘意图路由（遗留）。

基本 Agent 闭环后，工具/生图/站内帖邀请已由 graphs.mascot_graph 的 tool_planner 统一决策。
本模块保留 parse/history 辅助，避免外部误引用立刻炸掉；勿再作为主决策入口。
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings

logger = logging.getLogger(__name__)

_IMAGE_ACTION = "IMAGE"
_CHAT_ACTION = "CHAT"


def decide_mascot_action(
    message: str,
    history: list[dict[str, str]] | None,
) -> tuple[str, str, str, bool, str, bool, dict[str, Any]]:
    """遗留接口：请改用 mascot_graph.tool_planner。仍返回兼容元组供旧调用方兜底。"""
    conversation = _format_history(history)
    model = str(settings.dashscope.get("model_text_flash") or settings.dashscope.get("model_text") or "qwen3.7-flash")
    system = """你是论坛看板娘的受控 Supervisor。判断本轮生图、复杂度和是否适合询问站内帖子检索。
仅当用户明确要求“由你新生成一张图”时 action 才能为 IMAGE；用户想看、查找、搜索、展示、识别或评价已有图片，
即使消息里出现“图片”“画面”“图”，也都必须是 CHAT，后续联网链路会处理图集。
complexity 只能是 SIMPLE 或 COMPLEX。
suggest_related_search=true 仅限自然邀请站内帖检索有价值时。
need_search_images=true 表示应联网并展示图集。只输出合法 JSON：
{"action":"CHAT|IMAGE","image_prompt":"","complexity":"SIMPLE|COMPLEX","suggest_related_search":false,"related_search_query":"","need_search_images":false}"""
    user = f"最近对话：\n{conversation}\n\n本轮用户：{message[:2000]}"
    try:
        raw, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": system}, {"role": "user", "content": user}],
            temperature=0.0,
        )
    except Exception:
        logger.exception("看板娘 Supervisor 意图判断失败")
        return _CHAT_ACTION, "", "SIMPLE", False, "", False, {"model_code": model, "estimated": True}
    data = _parse_json(raw)
    action = str(data.get("action") or "").strip().upper() if data else _CHAT_ACTION
    prompt = str(data.get("image_prompt") or "").strip() if data else ""
    complexity = str(data.get("complexity") or "").strip().upper() if data else "SIMPLE"
    if complexity not in {"SIMPLE", "COMPLEX"}:
        complexity = "SIMPLE"
    suggest = bool(data.get("suggest_related_search")) if data else False
    query = str(data.get("related_search_query") or "").strip() if data else ""
    need_search_images = bool(data.get("need_search_images")) if data else False
    if not suggest or not query:
        suggest = False
        query = ""
    if action != _IMAGE_ACTION or not prompt:
        return _CHAT_ACTION, "", complexity, suggest, query[:500], need_search_images, usage
    return _IMAGE_ACTION, prompt[:1600], complexity, False, "", False, usage


def _format_history(history: list[dict[str, str]] | None) -> str:
    rows: list[str] = []
    for item in (history or [])[-6:]:
        role = str(item.get("role") or "").strip().lower()
        content = str(item.get("content") or "").strip()
        if role in {"user", "assistant"} and content:
            rows.append(f"{role}: {content[:500]}")
    return "\n".join(rows) or "（无）"


def _parse_json(raw: str) -> dict[str, Any] | None:
    match = re.search(r"\{[\s\S]*\}", (raw or "").strip())
    if not match:
        return None
    try:
        value = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None
