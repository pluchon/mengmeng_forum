"""受控文本生成能力。"""

from __future__ import annotations

from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings


def run_text_generation(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    normalized_kind = kind.strip().lower()
    dash = settings.dashscope
    if normalized_kind == "qwen_flash":
        model = dash.get("model_text_flash") or dash.get("model_text") or "qwen3.6-flash"
    elif normalized_kind == "qwen_pro":
        model = dash.get("model_text_deep") or "qwen3.7-max"
    else:
        raise ValueError(f"未知文本模型路由: {kind}")
    text, usage = dashscope_chat_completion(model, messages, temperature=0.35)
    return text.strip(), usage
