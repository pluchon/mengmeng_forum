"""Dashscope 文生图（z-image-turbo 等多模态 generation 端点）."""

from __future__ import annotations

import logging
from typing import Any

import requests

from clients.llm import dashscope_api_key
from config import settings

logger = logging.getLogger(__name__)

_DEFAULT_Z_IMAGE_URL = (
    "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
)


def _z_image_api_url() -> str:
    ds = settings.dashscope
    return (ds.get("image_generation_url") or _DEFAULT_Z_IMAGE_URL).strip()


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


def dashscope_text_to_image(prompt: str, *, model: str | None = None) -> tuple[str, dict[str, Any]]:
    """
    返回 (image_url, usage_dict)。
    z-image-turbo 使用 multimodal-generation 同步接口，勿走旧版 ImageSynthesis 异步任务。
    """
    key = dashscope_api_key()
    if not key:
        raise ValueError("DASHSCOPE_API_KEY 未配置")

    model_name = model or settings.dashscope.get("model_image_normal") or "z-image-turbo"
    text = (prompt or "").strip()[:800]
    if not text:
        raise ValueError("生图提示词不能为空")

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
        "parameters": {
            "prompt_extend": False,
            "size": "1024*1024",
        },
    }
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    url = _z_image_api_url()

    try:
        r = requests.post(url, headers=headers, json=payload, timeout=120)
    except Exception as e:
        logger.exception("Dashscope z-image HTTP 请求异常")
        raise ValueError(f"Dashscope 生图失败: {e}") from e

    try:
        data = r.json()
    except Exception as e:
        logger.warning("Dashscope 生图响应非 JSON: %s", r.text[:500])
        raise ValueError(f"Dashscope 生图失败: 无效响应") from e

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
    }
    return image_url, usage
