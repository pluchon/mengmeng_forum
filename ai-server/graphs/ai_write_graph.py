"""
AI 写作（DeepSeek / 通义千问 / Gemini via HuanAPI）.
"""

from __future__ import annotations

import logging
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_messages
from config import settings

logger = logging.getLogger(__name__)


def _huanapi_chat(model: str, messages: list[dict[str, Any]], *, api_key: str | None = None) -> tuple[str, dict[str, Any]]:
    hu = settings.huanapi
    base = str(hu.get("base_url") or "https://www.huanapi.com")
    key = (api_key or hu.get("gemini_key") or "").strip()
    text, usage = huanapi_messages(base, key, model, messages)
    usage["model_code"] = model
    return text, usage


def _dispatch_llm(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    kind = kind.strip().lower()

    ds = settings.deepseek
    dash = settings.dashscope
    hu = settings.huanapi

    if kind in ("deepseek_flash", "deepseek_pro"):
        model = ds.get("model_flash") if kind == "deepseek_flash" else ds.get("model_pro")
        base = ds.get("base_url") or "https://api.deepseek.com/v1"
        key = ds.get("api_key") or ""
        return deepseek_chat_completion(base, key, model, messages)

    if kind in ("qwen_flash", "qwen_pro"):
        model = (
            dash.get("model_text_flash") or dash.get("model_text") or "qwen3.6-flash"
            if kind == "qwen_flash"
            else dash.get("model_text_deep") or "qwen3.7-max"
        )
        text, usage = dashscope_chat_completion(model, messages, temperature=0.6)
        return text, usage

    if kind == "gemini_pro":
        model = hu.get("model_gemini_deep") or "gemini-3.1-pro"
        return _huanapi_chat(str(model), messages)

    if kind in ("claude_haiku", "claude_sonnet"):
        model = (
            hu.get("model_claude_haiku") or "claude-haiku-4-5"
            if kind == "claude_haiku"
            else hu.get("model_claude_sonnet") or "claude-sonnet-4-6"
        )
        key = (hu.get("claude_key") or hu.get("gemini_key") or "").strip()
        return _huanapi_chat(str(model), messages, api_key=key)

    raise ValueError(f"未知 kind: {kind}")


def run_ai_write(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    text, usage = _dispatch_llm(kind, messages)
    return text.strip(), usage
