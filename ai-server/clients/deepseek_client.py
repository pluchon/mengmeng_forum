"""DeepSeek OpenAI 兼容 /chat/completions."""

from __future__ import annotations

import logging
from typing import Any

import requests

logger = logging.getLogger(__name__)


def deepseek_chat_completion(
    base_url: str,
    api_key: str,
    model: str,
    messages: list[dict[str, Any]],
    *,
    timeout: int = 120,
) -> tuple[str, dict[str, Any]]:
    if not api_key:
        raise ValueError("deepseek api_key 未配置，请设置 DEEPSEEK_API_KEY 或 config.yaml deepseek.api_key")
    url = base_url.rstrip("/") + "/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload = {"model": model, "messages": messages}
    r = requests.post(url, headers=headers, json=payload, timeout=timeout)
    if not r.ok:
        logger.warning("DeepSeek HTTP %s: %s", r.status_code, r.text[:500])
    r.raise_for_status()
    data = r.json()
    choice0 = (data.get("choices") or [{}])[0]
    msg = choice0.get("message") or {}
    content = msg.get("content")
    if isinstance(content, str) and content.strip():
        from clients.usage_util import usage_from_openai_style

        usage = usage_from_openai_style(data, model)
        return content.strip(), usage
    raise ValueError(f"DeepSeek 响应无法解析: {data!r}"[:500])
