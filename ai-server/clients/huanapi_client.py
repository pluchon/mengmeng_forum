"""HuanAPI：Gemini 风格 /messages 与 OpenAI 风格 /images/generations（按上游实际字段解析）."""

from __future__ import annotations

import logging
import time
from typing import Any

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logger = logging.getLogger(__name__)

GPT_IMAGE_MODEL = "gpt-image-2"
_IMAGE_TIMEOUT = 180
_RETRYABLE_EXC = (
    requests.exceptions.SSLError,
    requests.exceptions.ConnectionError,
    requests.exceptions.Timeout,
    requests.exceptions.ChunkedEncodingError,
)


def normalize_huanapi_v1_base(base_url: str) -> str:
    """统一为 …/v1，避免配置漏写子路径"""
    base = (base_url or "").strip().rstrip("/") or "https://www.huanapi.com"
    if not base.endswith("/v1"):
        base = base + "/v1"
    return base


def _huanapi_session() -> requests.Session:
    """带连接重试的 Session（应对 SSL EOF 等瞬时网络故障）"""
    retry = Retry(
        total=3,
        connect=3,
        read=3,
        backoff_factor=1.2,
        status_forcelist=(502, 503, 504),
        allowed_methods=frozenset(["POST"]),
        raise_on_status=False,
    )
    session = requests.Session()
    adapter = HTTPAdapter(max_retries=retry, pool_connections=4, pool_maxsize=4)
    session.mount("https://", adapter)
    session.mount("http://", adapter)
    return session


def _post_json(
    url: str,
    *,
    headers: dict[str, str],
    payload: dict[str, Any],
    timeout: int,
) -> requests.Response:
    """POST JSON；对 SSL/连接类错误额外手动重试（urllib3 不一定覆盖 SSLEOF）"""
    session = _huanapi_session()
    last_exc: Exception | None = None
    for attempt in range(1, 4):
        try:
            return session.post(url, headers=headers, json=payload, timeout=timeout)
        except _RETRYABLE_EXC as exc:
            last_exc = exc
            if attempt >= 3:
                break
            wait = 1.2 * attempt
            logger.warning(
                "HuanAPI POST 网络异常(第 %s 次) %s: %s，%.1fs 后重试",
                attempt,
                url,
                exc,
                wait,
            )
            time.sleep(wait)
    assert last_exc is not None
    raise last_exc


def _extract_text(data: Any) -> str:
    if data is None:
        return ""
    if isinstance(data, str):
        return data.strip()
    if not isinstance(data, dict):
        return str(data)
    choices = data.get("choices")
    if isinstance(choices, list) and choices:
        ch0 = choices[0]
        if isinstance(ch0, dict):
            msg = ch0.get("message") or {}
            if isinstance(msg, dict) and isinstance(msg.get("content"), str):
                return msg["content"].strip()
    out = data.get("output_text") or data.get("text")
    if isinstance(out, str) and out.strip():
        return out.strip()
    content = data.get("content")
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, dict) and isinstance(block.get("text"), str):
                parts.append(block["text"])
        if parts:
            return "\n".join(parts).strip()
    if isinstance(content, str):
        return content.strip()
    raise ValueError(f"HuanAPI messages 响应无法解析文本: {str(data)[:400]}")


def huanapi_messages(
    base_url: str,
    api_key: str,
    model: str,
    messages: list[dict[str, Any]],
    *,
    timeout: int = 120,
) -> tuple[str, dict[str, Any]]:
    if not api_key:
        raise ValueError("huanapi gemini_key 未配置（HUANAPI_GEMINI_KEY）")
    url = normalize_huanapi_v1_base(base_url) + "/messages"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload: dict[str, Any] = {"model": model, "messages": messages}
    r = _post_json(url, headers=headers, payload=payload, timeout=timeout)
    if not r.ok:
        logger.warning("HuanAPI messages HTTP %s: %s", r.status_code, r.text[:500])
    r.raise_for_status()
    data = r.json()
    from clients.usage_util import usage_from_openai_style

    text = _extract_text(data)
    usage = usage_from_openai_style(data if isinstance(data, dict) else {}, model)
    if usage.get("input_tokens", 0) == 0 and usage.get("output_tokens", 0) == 0:
        usage["estimated"] = True
    return text, usage


def _extract_image_url(data: Any) -> str:
    if isinstance(data, dict):
        if "data" in data and isinstance(data["data"], list) and data["data"]:
            item = data["data"][0]
            if isinstance(item, dict) and isinstance(item.get("url"), str):
                return item["url"]
            if isinstance(item, dict) and isinstance(item.get("b64_json"), str) and item["b64_json"].strip():
                b64 = item["b64_json"].strip()
                return f"data:image/png;base64,{b64}"
        if isinstance(data.get("url"), str):
            return data["url"]
        if isinstance(data.get("image_url"), str):
            return data["image_url"]
    raise ValueError(f"HuanAPI image 响应无法解析 URL: {str(data)[:400]}")


def _huanapi_error_message(r: requests.Response) -> str:
    try:
        js = r.json()
        err = js.get("error") if isinstance(js, dict) else None
        if isinstance(err, dict) and err.get("message"):
            return str(err["message"])
    except Exception:
        pass
    return (r.text or "")[:400]


def huanapi_images(
    base_url: str,
    api_key: str,
    model: str,
    prompt: str,
    *,
    timeout: int = _IMAGE_TIMEOUT,
) -> tuple[str, dict[str, Any]]:
    if not api_key:
        raise ValueError("huanapi image_key 未配置（HUANAPI_IMAGE_KEY）")
    m = GPT_IMAGE_MODEL
    configured = (str(model or "").strip() or GPT_IMAGE_MODEL)
    if configured != GPT_IMAGE_MODEL:
        logger.warning(
            "配置 model=%r 与 HuanAPI 官方名称不一致，已强制使用 %s",
            configured,
            GPT_IMAGE_MODEL,
        )

    base = normalize_huanapi_v1_base(base_url)
    url = base + "/images/generations"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    payload: dict[str, Any] = {"model": m, "prompt": prompt, "n": 1}

    logger.info("HuanAPI images POST %s model=%s", url, m)
    r = _post_json(url, headers=headers, payload=payload, timeout=timeout)
    if r.ok:
        data = r.json()
        image_url = _extract_image_url(data)
        return image_url, {
            "model_code": m,
            "input_tokens": 0,
            "output_tokens": 0,
            "images": 1,
            "estimated": False,
        }

    detail = _huanapi_error_message(r)
    logger.warning("HuanAPI images HTTP %s model=%s: %s", r.status_code, m, detail[:500])
    raise requests.HTTPError(
        f"HuanAPI images failed ({r.status_code}): {detail}",
        response=r,
    )
