"""Dashscope 文生图（wan2.7-image / wan2.7-image-pro，multimodal-generation 端点）."""

from __future__ import annotations

import logging
from typing import Any

import requests

from clients.llm import dashscope_api_key
from config import settings

logger = logging.getLogger(__name__)

_DEFAULT_IMAGE_GEN_URL = (
    "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
)

_WAN_PRO_MODELS = frozenset({"wan2.7-image-pro"})


def _image_api_url() -> str:
    ds = settings.dashscope
    return (ds.get("image_generation_url") or _DEFAULT_IMAGE_GEN_URL).strip()


def _default_size_for_model(model_name: str) -> str:
    # wan2.7 官方推荐 size 为档位字符串；pro 默认可 2K，调用方可升 4K
    if model_name.startswith("wan2.7"):
        return "2K"
    return "1024*1024"


def _extract_image_url(data: dict[str, Any]) -> str | None:
    output = data.get("output") or {}
    choices = output.get("choices") or []
    if not choices:
        return None
    message = (choices[0] or {}).get("message") or {}
    content = message.get("content") or []
    for part in content:
        if not isinstance(part, dict):
            continue
        url = part.get("image")
        if url:
            return str(url)
    return None


def dashscope_text_to_image(
    prompt: str,
    *,
    model: str | None = None,
    size: str | None = None,
) -> tuple[str, dict[str, Any]]:
    """
    返回 (image_url, usage_dict)。
    wan2.7-image / wan2.7-image-pro 使用 multimodal-generation 同步接口，
    勿走旧版 ImageSynthesis 异步任务。
    """
    key = dashscope_api_key()
    if not key:
        raise ValueError("DASHSCOPE_API_KEY 未配置")

    model_name = model or settings.dashscope.get("model_image_normal") or "wan2.7-image"
    text = (prompt or "").strip()[:800]
    if not text:
        raise ValueError("生图提示词不能为空")

    resolved_size = (size or "").strip() or _default_size_for_model(model_name)
    parameters: dict[str, Any] = {
        "prompt_extend": False,
        "size": resolved_size,
        "n": 1,
        "watermark": False,
    }
    # wan2.7 支持关闭思考模式以降低时延；非 wan 模型忽略该字段亦可
    if model_name.startswith("wan2.7"):
        parameters["thinking_mode"] = False

    payload: dict[str, Any] = {
        "model": model_name,
        "input": {
            "messages": [
                {
                    "role": "user",
                    "content": [{"text": text}],
                },
            ],
        },
        "parameters": parameters,
    }
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    url = _image_api_url()

    try:
        r = requests.post(url, headers=headers, json=payload, timeout=240)
    except Exception as e:
        logger.exception("Dashscope wan 生图 HTTP 请求异常 model=%s", model_name)
        raise ValueError(f"Dashscope 生图失败: {e}") from e

    try:
        data = r.json()
    except Exception as e:
        logger.warning("Dashscope 生图响应非 JSON: %s", r.text[:500])
        raise ValueError("Dashscope 生图失败: 无效响应") from e

    if not r.ok:
        code = data.get("code") or r.status_code
        message = data.get("message") or r.text[:300]
        logger.warning("Dashscope 生图 HTTP 业务失败: %s %s", code, message)
        raise ValueError(f"Dashscope 生图失败: {message}")

    image_url = _extract_image_url(data)
    if not image_url:
        logger.warning("Dashscope 生图无图片 URL: %s", str(data)[:500])
        raise ValueError(f"Dashscope 生图无结果: {data!r}"[:400])

    usage_block = data.get("usage") or {}
    usage = {
        "model_code": model_name,
        "input_tokens": int(usage_block.get("input_tokens") or 0),
        "output_tokens": int(usage_block.get("output_tokens") or 0),
        "images": int(usage_block.get("image_count") or 1),
        "estimated": False,
        "is_pro": model_name in _WAN_PRO_MODELS,
    }
    return image_url, usage


def dashscope_premium_text_to_image(
    prompt: str,
    *,
    size: str | None = None,
) -> tuple[str, dict[str, Any]]:
    """DashScope 进阶档（wan2.7-image-pro）。"""
    model = settings.dashscope.get("model_image_premium") or "wan2.7-image-pro"
    return dashscope_text_to_image(prompt, model=model, size=size or "2K")
