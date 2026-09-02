"""Dashscope OpenAI 兼容 /chat/completions（qwen3.7 / qwen3-vl 等）."""

from __future__ import annotations

from collections.abc import Iterator
import json
import logging
import time
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

# 复用连接，省掉每次调用的 TCP + TLS 握手
_SESSION = requests.Session()

# 只有这些状态码值得重试：限流与网关抖动是瞬时的。
# 4xx 参数/鉴权错误重试多少次都是同样的结果，只会拖长用户等待
_RETRYABLE_STATUS = frozenset({408, 409, 425, 429, 500, 502, 503, 504})


def _is_retryable(exc: Exception) -> bool:
    if isinstance(exc, requests.HTTPError):
        response = exc.response
        return response is not None and response.status_code in _RETRYABLE_STATUS
    # 连接被拒、读超时这类网络层异常同样属于瞬时故障
    if isinstance(exc, (requests.ConnectionError, requests.Timeout)):
        return True
    # 响应体解析不出内容，多半是模型这一次抽风，值得再要一次
    return isinstance(exc, ValueError)


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


def _thinking_payload(model: str) -> dict[str, Any]:
    """把 Qwen3 的思考模式显式钉住。

    Qwen3 是混合推理模型，开不开思考由服务端默认值决定——代码一行不改，
    换个模型版本或服务端改默认，所有调用的延迟就会成倍上涨。所以显式写死，
    要开也只能从配置里开。只对 qwen3 系列发这个参数，别的模型会 400。
    """
    if not str(model or "").lower().startswith("qwen3"):
        return {}
    return {"enable_thinking": bool(settings.dashscope.get("enable_thinking", False))}


def dashscope_chat_completion(
    model: str,
    messages: list[dict[str, Any]],
    *,
    temperature: float = 0.0,
    timeout: int = 45,
    response_format: dict[str, Any] | None = None,
    max_tokens: int | None = None,
    retries: int = 1,
) -> tuple[str, dict[str, Any]]:
    """调用一次对话补全。

    默认对瞬时故障重试一次：调用方的配额在进入这里之前就已经扣掉了，
    一次限流或网关抖动就降级到兜底，等于让用户白付一次额度。
    只重试 429 / 5xx 与网络层异常，4xx 直接抛。
    """
    last_error: Exception | None = None
    for attempt in range(max(0, retries) + 1):
        try:
            return _post_chat_completion(
                model, messages, temperature=temperature,
                timeout=timeout, response_format=response_format,
                max_tokens=max_tokens,
            )
        except Exception as exc:
            last_error = exc
            if attempt >= retries or not _is_retryable(exc):
                raise
            # 线性退避即可，这里最多重试一两次，指数退避没有意义
            time.sleep(0.6 * (attempt + 1))
            logger.warning(
                "Dashscope 调用失败将重试 model=%s attempt=%s error=%s",
                model, attempt + 1, type(exc).__name__,
            )
    assert last_error is not None
    raise last_error


def _post_chat_completion(
    model: str,
    messages: list[dict[str, Any]],
    *,
    temperature: float,
    timeout: int,
    response_format: dict[str, Any] | None,
    max_tokens: int | None = None,
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
    if max_tokens and max_tokens > 0:
        payload["max_tokens"] = int(max_tokens)
    payload.update(_thinking_payload(model))
    r = _SESSION.post(url, headers=headers, json=payload, timeout=timeout)
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
    retries: int = 1,
) -> tuple[str, dict[str, Any]]:
    """强制 JSON 对象输出；重试策略见 dashscope_chat_completion。"""
    return dashscope_chat_completion(
        model,
        messages,
        temperature=temperature,
        timeout=timeout,
        response_format={"type": "json_object"},
        retries=retries,
    )


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
