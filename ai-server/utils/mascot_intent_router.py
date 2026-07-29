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
) -> tuple[str, str, str, bool, str, bool, dict[str, Any]]:
    """根据最近对话决定生图、复杂度与是否主动询问站内检索。"""
    conversation = _format_history(history)
    model = str(settings.dashscope.get("model_text_flash") or settings.dashscope.get("model_text") or "qwen3.6-flash")
    system = """你是论坛看板娘的受控 Supervisor。判断本轮生图、复杂度和是否适合询问站内帖子检索。
仅当用户明确说要画、生成图片、绘制或为其出图时 action 才能为 IMAGE；询问画法、评价图片、
描述画面、要求搜索图片或普通聊天都必须是 CHAT。你可以参考历史对话补全用户已经明确的主体、场景、风格。
action=IMAGE 时 image_prompt 必须是一段可独立交给生图模型的中文画面描述，保留已知事实，不要编造人物身份、
地名、品牌、图片文字或敏感内容；action=CHAT 时 image_prompt 必须为空。
complexity 只能是 SIMPLE 或 COMPLEX：闲聊、单点问答、简短改写、简单站点功能提问属于 SIMPLE；
需要多步推理、比较取舍、方案规划、长篇结构化创作或深入分析才是 COMPLEX。
suggest_related_search=true 仅限用户确实在谈一个可被部落帖子帮助的话题，并且自然地问一句“要不要我帮你看看部落里有没有人聊过？”会有价值；
不要每轮都建议，不要对寒暄、站点操作、已有明确答案、图片生成请求建议。related_search_query 必须是结合最近上下文和本轮内容的简洁检索语句；
不建议时 related_search_query 为空。need_search_images=true 仅当本轮需要联网查询、用户在了解新实体、地点或作品且图片确实有助于理解时才允许；普通问答、新闻、操作说明一律 false。只输出合法 JSON，不要 Markdown：
{"action":"CHAT|IMAGE","image_prompt":"","complexity":"SIMPLE|COMPLEX","suggest_related_search":false,"related_search_query":"","need_search_images":false}"""
    user = f"最近对话：\n{conversation}\n\n本轮用户：{message[:2000]}"
    try:
        raw, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": system}, {"role": "user", "content": user}],
            temperature=0.1,
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
