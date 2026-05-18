"""
AI 写作（DeepSeek / HuanAPI Gemini）.
"""

from __future__ import annotations

import logging
from typing import Any

from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_messages, normalize_huanapi_v1_base
from config import settings

logger = logging.getLogger(__name__)


def _dispatch_llm(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    kind = kind.strip().lower()
    ds = settings.deepseek
    hu = settings.huanapi

    if kind in ("deepseek_flash", "deepseek_pro"):
        model = ds.get("model_flash") if kind == "deepseek_flash" else ds.get("model_pro")
        base = ds.get("base_url") or "https://api.deepseek.com/v1"
        key = ds.get("api_key") or ""
        return deepseek_chat_completion(base, key, model, messages)

    if kind in ("gemini_flash", "gemini_pro"):
        model = hu.get("model_gemini_flash") if kind == "gemini_flash" else hu.get("model_gemini_pro")
        base = normalize_huanapi_v1_base(str(hu.get("base_url") or "https://www.huanapi.com"))
        key = hu.get("gemini_key") or ""
        return huanapi_messages(base, key, model, messages)

    raise ValueError(f"未知 kind: {kind}")


def run_ai_write(kind: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
    text, usage = _dispatch_llm(kind, messages)
    return text.strip(), usage
