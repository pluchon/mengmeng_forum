"""看板娘 Supervisor 的受控意图判断。"""

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
) -> tuple[str, str, dict[str, Any]]:
    """根据最近对话决定保留聊天或委派生图，并产出独立画面提示词。"""
    conversation = _format_history(history)
    model = str(settings.dashscope.get("model_text_flash") or settings.dashscope.get("model_text") or "qwen3.6-flash")
    system = """你是论坛看板娘的受控 Supervisor。只判断本轮是否明确要求立刻生成一张图片。
仅当用户明确说要画、生成图片、绘制或为其出图时 action 才能为 IMAGE；询问画法、评价图片、
描述画面、要求搜索图片或普通聊天都必须是 CHAT。你可以参考历史对话补全用户已经明确的主体、场景、风格。
action=IMAGE 时 image_prompt 必须是一段可独立交给生图模型的中文画面描述，保留已知事实，不要编造人物身份、
地名、品牌、图片文字或敏感内容；action=CHAT 时 image_prompt 必须为空。
只输出合法 JSON，不要 Markdown：{"action":"CHAT|IMAGE","image_prompt":""}"""
    user = f"最近对话：\n{conversation}\n\n本轮用户：{message[:2000]}"
    try:
        raw, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": system}, {"role": "user", "content": user}],
            temperature=0.1,
        )
    except Exception:
        logger.exception("看板娘 Supervisor 意图判断失败")
        return _CHAT_ACTION, "", {"model_code": model, "estimated": True}
    data = _parse_json(raw)
    action = str(data.get("action") or "").strip().upper() if data else _CHAT_ACTION
    prompt = str(data.get("image_prompt") or "").strip() if data else ""
    if action != _IMAGE_ACTION or not prompt:
        return _CHAT_ACTION, "", usage
    return _IMAGE_ACTION, prompt[:1600], usage


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
