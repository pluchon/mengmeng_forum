"""AI 写作。"""

from __future__ import annotations

import logging
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings

logger = logging.getLogger(__name__)


def _dispatch_llm(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    kind = kind.strip().lower()

    dash = settings.dashscope
    if kind in ("qwen_flash", "qwen_pro"):
        model = (
            dash.get("model_text_flash") or dash.get("model_text") or "qwen3.6-flash"
            if kind == "qwen_flash"
            else dash.get("model_text_deep") or "qwen3.7-max"
        )
        text, usage = dashscope_chat_completion(model, messages, temperature=0.6)
        return text, usage

    raise ValueError(f"未知 kind: {kind}")


def run_ai_write(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    text, usage = _dispatch_llm(kind, messages)
    return text.strip(), usage
