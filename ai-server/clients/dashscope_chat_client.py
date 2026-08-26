"""Dashscope OpenAI 兼容 /chat/completions（qwen3.7 / qwen3-vl 等）."""

from __future__ import annotations

from collections.abc import Iterator
import json
import logging
from typing import Any

import requests
from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from pydantic import Field

from clients.usage_util import usage_from_openai_style
from config import settings

logger = logging.getLogger(__name__)

_DEFAULT_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"


def dashscope_compat_base() -> str:
    base = (settings.dashscope.get("base_url") or _DEFAULT_BASE).strip().rstrip("/")
    return base or _DEFAULT_BASE


def lc_messages_to_openai(messages: list[BaseMessage]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for m in messages:
        if m.type == "system":
            out.append({"role": "system", "content": str(m.content)})
        elif m.type == "ai":
            out.append({"role": "assistant", "content": str(m.content)})
        elif m.type == "human":
            c = m.content
            if isinstance(c, list):
                parts: list[dict[str, Any]] = []
                for item in c:
                    if not isinstance(item, dict):
                        continue
                    if "image" in item:
                        parts.append({
                            "type": "image_url",
                            "image_url": {"url": str(item["image"])},
                        })
                    elif "video" in item:
                        parts.append({
                            "type": "video_url",
                            "video_url": {"url": str(item["video"])},
                        })
                    elif "text" in item:
                        parts.append({"type": "text", "text": str(item["text"])})
                out.append({"role": "user", "content": parts if parts else ""})
            else:
                out.append({"role": "user", "content": str(c)})
    return out


def dashscope_chat_completion(
    model: str,
    messages: list[dict[str, Any]],
    *,
    temperature: float = 0.0,
    timeout: int = 45,
    response_format: dict[str, Any] | None = None,
) -> tuple[str, dict[str, Any]]:
    api_key = settings.dashscope.get("api_key") or ""
    base = dashscope_compat_base()
    url = f"{base}/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
    }
    if response_format:
        payload["response_format"] = response_format
    r = requests.post(url, headers=headers, json=payload, timeout=timeout)
    if not r.ok:
        logger.warning("Dashscope HTTP %s: %s", r.status_code, r.text[:500])
        r.raise_for_status()
    data = r.json()
    choice0 = (data.get("choices") or [{}])[0]
    msg = choice0.get("message") or {}
    content = msg.get("content")
    if isinstance(content, str) and content.strip():
        usage = usage_from_openai_style(data, model)
        return content.strip(), usage
    raise ValueError(f"Dashscope 响应无法解析: {data!r}"[:500])


def json_chat_completion(
    model: str,
    messages: list[dict[str, Any]],
    *,
    temperature: float = 0.2,
    timeout: int = 120,
    retries: int = 0,
) -> tuple[str, dict[str, Any]]:
    """强制 JSON 对象输出；可选轻量重试（默认不重试，避免放大费用）。"""
    last_error: Exception | None = None
    for attempt in range(max(0, retries) + 1):
        try:
            return dashscope_chat_completion(
                model,
                messages,
                temperature=temperature,
                timeout=timeout,
                response_format={"type": "json_object"},
            )
        except Exception as exc:
            last_error = exc
            if attempt < retries:
                logger.warning(
                    "json_chat_completion 失败将重试 model=%s attempt=%s",
                    model,
                    attempt + 1,
                )
                continue
            raise
    assert last_error is not None
    raise last_error


def dashscope_stream_text(
    model: str,
    messages: list[dict[str, Any]],
    *,
    temperature: float = 0.3,
    timeout: int = 45,
) -> Iterator[tuple[str, Any]]:
    """流式事件：('text', 片段) 或 ('usage', dict)。"""
    api_key = settings.dashscope.get("api_key") or ""
    base = dashscope_compat_base()
    url = f"{base}/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "stream": True,
        "stream_options": {"include_usage": True},
    }
    with requests.post(url, headers=headers, json=payload, timeout=timeout, stream=True) as r:
        if not r.ok:
            logger.warning("Dashscope stream HTTP %s: %s", r.status_code, r.text[:500])
            r.raise_for_status()
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


def dashscope_stream_text_legacy(
    model: str,
    messages: list[dict[str, Any]],
    *,
    temperature: float = 0.3,
    timeout: int = 45,
) -> Iterator[str]:
    """兼容旧调用：仅 yield 文本片段。"""
    for event in dashscope_stream_text(model, messages, temperature=temperature, timeout=timeout):
        if event[0] == "text":
            yield event[1]


class DashscopeChatModel(BaseChatModel):
    """LangChain Runnable，供 Prompt | model 与 .invoke(messages) 使用。"""

    model_name: str = Field(default="qwen3.7-flash")
    temperature: float = 0.0

    @property
    def _llm_type(self) -> str:
        return "dashscope-compat"

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        from config import settings

        openai_msgs = lc_messages_to_openai(messages)
        timeout = int(settings.audit.get("text_audit_timeout", 45))
        text, _ = dashscope_chat_completion(
            self.model_name,
            openai_msgs,
            temperature=self.temperature,
            timeout=timeout,
        )
        return ChatResult(generations=[ChatGeneration(message=AIMessage(content=text))])
