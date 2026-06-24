"""DeepSeek OpenAI 兼容 /chat/completions."""

from __future__ import annotations

import json
import logging
from typing import Any, Iterator

import requests

logger = logging.getLogger(__name__)


def deepseek_chat_completion(
    base_url: str,
    api_key: str,
    model: str,
    messages: list[dict[str, Any]],
    *,
    timeout: int = 120,
    max_tokens: int | None = None,
    temperature: float | None = None,
) -> tuple[str, dict[str, Any]]:
    if not api_key:
        raise ValueError("deepseek api_key 未配置，请设置 DEEPSEEK_API_KEY 或 config.yaml deepseek.api_key")
    url = base_url.rstrip("/") + "/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload: dict[str, Any] = {"model": model, "messages": messages}
    if max_tokens is not None:
        payload["max_tokens"] = max_tokens
    if temperature is not None:
        payload["temperature"] = temperature
    r = requests.post(url, headers=headers, json=payload, timeout=timeout)
    if not r.ok:
        body = (r.text or "")[:500]
        logger.warning("DeepSeek HTTP %s model=%s: %s", r.status_code, model, body)
        if r.status_code == 401:
            raise ValueError(
                "DeepSeek API 密钥无效：DEEPSEEK_API_KEY 须为 platform.deepseek.com 申请的密钥（不能与 DASHSCOPE_API_KEY 混用）"
            )
        raise ValueError(f"DeepSeek API 错误 ({r.status_code}): {body[:240]}")
    data = r.json()
    choice0 = (data.get("choices") or [{}])[0]
    msg = choice0.get("message") or {}
    content = msg.get("content")
    if isinstance(content, str) and content.strip():
        from clients.usage_util import usage_from_openai_style

        usage = usage_from_openai_style(data, model)
        return content.strip(), usage
    raise ValueError(f"DeepSeek 响应无法解析: {data!r}"[:500])


def deepseek_stream_text(
    base_url: str,
    api_key: str,
    model: str,
    messages: list[dict[str, Any]],
    *,
    timeout: int = 180,
) -> Iterator[tuple[str, Any]]:
    from clients.usage_util import usage_from_openai_style

    if not api_key:
        raise ValueError("deepseek api_key 未配置")
    url = base_url.rstrip("/") + "/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "stream": True,
        "stream_options": {"include_usage": True},
    }
    with requests.post(url, headers=headers, json=payload, timeout=timeout, stream=True) as r:
        if not r.ok:
            body = (r.text or "")[:500]
            logger.warning("DeepSeek stream HTTP %s model=%s: %s", r.status_code, model, body)
            if r.status_code == 401:
                raise ValueError(
                    "DeepSeek API 密钥无效：DEEPSEEK_API_KEY 须为 platform.deepseek.com 申请的密钥（不能与 DASHSCOPE_API_KEY 混用）"
                )
            raise ValueError(f"DeepSeek API 错误 ({r.status_code}): {body[:240]}")
        for raw in r.iter_lines(decode_unicode=True):
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
                yield ("usage", usage_from_openai_style(chunk, model))
                continue
            delta = (chunk.get("choices") or [{}])[0].get("delta") or {}
            text = delta.get("content")
            if text:
                yield ("text", str(text))
