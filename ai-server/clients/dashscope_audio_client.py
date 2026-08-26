"""DashScope OpenAI 兼容 /chat/completions 音频理解（qwen3-omni-flash）。"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

import requests

from clients.usage_util import usage_from_openai_style
from config import settings

logger = logging.getLogger(__name__)

_DEFAULT_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
_FORMAT_BY_EXT = {
    "wav": "wav",
    "mp3": "mp3",
    "flac": "flac",
    "m4a": "m4a",
    "aac": "aac",
    "ogg": "ogg",
}


def dashscope_compat_base() -> str:
    base = (settings.dashscope.get("base_url") or _DEFAULT_BASE).strip().rstrip("/")
    return base or _DEFAULT_BASE


def omni_model() -> str:
    return str(settings.dashscope.get("model_omni") or "qwen3-omni-flash")


def guess_audio_format(audio_url: str) -> str:
    match = re.search(r"\.([a-z0-9]{2,5})(?:\?|$)", audio_url.strip().lower())
    if match:
        ext = match.group(1)
        if ext in _FORMAT_BY_EXT:
            return _FORMAT_BY_EXT[ext]
    return "mp3"


def dashscope_audio_completion(
    audio_url: str,
    prompt: str,
    *,
    audio_format: str | None = None,
    temperature: float = 0.0,
    timeout: int = 120,
    response_format: dict[str, Any] | None = None,
) -> tuple[str, dict[str, Any]]:
    """传入公网 audio URL 与文本 prompt，返回模型文本与用量。"""
    url = audio_url.strip()
    if not url:
        raise ValueError("audio_url 不能为空")
    model = omni_model()
    base = dashscope_compat_base()
    endpoint = f"{base}/chat/completions"
    api_key = settings.dashscope.get("api_key") or ""
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    fmt = (audio_format or guess_audio_format(url)).strip().lower()
    payload: dict[str, Any] = {
        "model": model,
        "messages": [{
            "role": "user",
            "content": [
                {"type": "input_audio", "input_audio": {"data": url, "format": fmt}},
                {"type": "text", "text": prompt},
            ],
        }],
        "modalities": ["text"],
        "temperature": temperature,
        "stream": True,
        "stream_options": {"include_usage": True},
    }
    if response_format:
        payload["response_format"] = response_format
    text_parts: list[str] = []
    usage: dict[str, Any] = {}
    with requests.post(endpoint, headers=headers, json=payload, timeout=timeout, stream=True) as response:
        if not response.ok:
            logger.warning("DashScope audio HTTP %s: %s", response.status_code, response.text[:500])
            response.raise_for_status()
        for raw in response.iter_lines(decode_unicode=True):
            if not raw or not raw.startswith("data:"):
                continue
            data = raw[5:].strip()
            if data == "[DONE]":
                break
            try:
                chunk = json.loads(data)
            except json.JSONDecodeError:
                continue
            if chunk.get("usage"):
                usage = usage_from_openai_style(chunk, model)
                continue
            delta = (chunk.get("choices") or [{}])[0].get("delta") or {}
            text = delta.get("content")
            if text:
                text_parts.append(str(text))
    content = "".join(text_parts).strip()
    if content:
        return content, usage
    raise ValueError("DashScope 音频响应无法解析")
