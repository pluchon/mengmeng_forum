"""
Dashscope 对话客户端工厂（OpenAI 兼容模式）.
Rerank 仍走 dashscope SDK.
"""
from __future__ import annotations

import logging

from clients.dashscope_chat_client import DashscopeChatModel
from config import settings

logger = logging.getLogger(__name__)

_DS = settings.dashscope


def text_llm(temperature: float = 0.0, *, model_name: str | None = None) -> DashscopeChatModel:
    """文本审核 / 摘要"""
    model = model_name or _DS.get("model_text", "qwen3.7-flash")
    return DashscopeChatModel(model_name=model, temperature=temperature)


def vision_llm(temperature: float = 0.0, *, model_name: str | None = None) -> DashscopeChatModel:
    """视觉模型: 图片描述"""
    model = model_name or _DS.get("model_vision", "qwen3-vl-flash")
    return DashscopeChatModel(model_name=model, temperature=temperature)


def vision_llm_fallback(temperature: float = 0.0) -> DashscopeChatModel:
    plus = _DS.get("model_vision_fallback", "qwen3-vl-plus")
    return vision_llm(temperature=temperature, model_name=plus)


def dashscope_chat_model(model_key: str, temperature: float, *, default: str) -> DashscopeChatModel:
    name = _DS.get(model_key) or default
    return DashscopeChatModel(model_name=name, temperature=temperature)


def rerank_model_name() -> str:
    return _DS.get("model_rerank", "qwen3-rerank")


def embedding_model_name() -> str:
    return _DS.get("model_embedding_rag", "qwen3-vl-embedding")


def embedding_text_fallback_model_name() -> str:
    """TextEmbedding API 专用；勿填 qwen3-vl-embedding / qwen3-rerank."""
    return _DS.get("model_embedding_text_fallback", "text-embedding-v4")


def flash_model_name() -> str:
    """默认低成本文本模型（全仓统一兜底名）。"""
    return str(_DS.get("model_text_flash") or _DS.get("model_text") or "qwen3.7-flash").strip()


def deep_model_name() -> str:
    """高成本文本模型（仅边界/复杂场景升级）。"""
    return str(_DS.get("model_text_deep") or "qwen3.7-max").strip()


def dashscope_api_key() -> str:
    return _DS.get("api_key")
