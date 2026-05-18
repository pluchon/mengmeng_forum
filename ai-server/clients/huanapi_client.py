"""HuanAPI：Gemini 风格 /messages 与 OpenAI 风格 /images/generations（按上游实际字段解析）."""

from __future__ import annotations

import logging
from typing import Any

import requests

logger = logging.getLogger(__name__)


def normalize_huanapi_v1_base(base_url: str) -> str:
    """统一为 …/v1，避免配置漏写子路径"""
    base = (base_url or "").strip().rstrip("/") or "https://www.huanapi.com"
    if not base.endswith("/v1"):
        base = base + "/v1"
    return base


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
    # Anthropic-like
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
    r = requests.post(url, headers=headers, json=payload, timeout=timeout)
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
            # OpenAI style: b64_json（前端可直接用 data URL 展示）
            if isinstance(item, dict) and isinstance(item.get("b64_json"), str) and item["b64_json"].strip():
                b64 = item["b64_json"].strip()
                return f"data:image/png;base64,{b64}"
        if isinstance(data.get("url"), str):
            return data["url"]
        if isinstance(data.get("image_url"), str):
            return data["image_url"]
    raise ValueError(f"HuanAPI image 响应无法解析 URL: {str(data)[:400]}")


def huanapi_images(
    base_url: str,
    api_key: str,
    model: str,
    prompt: str,
    *,
    timeout: int = 180,
) -> tuple[str, dict[str, Any]]:
    if not api_key:
        raise ValueError("huanapi image_key 未配置（HUANAPI_IMAGE_KEY）")
    base = normalize_huanapi_v1_base(base_url)
    url = base + "/images/generations"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}

    candidates = [str(model or "").strip()]
    for a in ("gpt-image2", "gpt-image-1"):
        if a and a not in candidates:
            candidates.append(a)

    last_err: Exception | None = None
    for m in [c for c in candidates if c]:
        payload: dict[str, Any] = {"model": m, "prompt": prompt, "n": 1}
        try:
            r = requests.post(url, headers=headers, json=payload, timeout=timeout)
        except Exception as e:
            last_err = e
            break
        if r.ok:
            data = r.json()
            url = _extract_image_url(data)
            return url, {
                "model_code": m,
                "input_tokens": 0,
                "output_tokens": 0,
                "images": 1,
                "estimated": False,
            }

        logger.warning("HuanAPI images HTTP %s: %s", r.status_code, r.text[:500])

        try:
            js = r.json()
            err = js.get("error") if isinstance(js, dict) else None
            code = err.get("code") if isinstance(err, dict) else None
            msg = err.get("message") if isinstance(err, dict) else ""
            if code in ("model_not_found", "model_not_available") or ("No available channel" in str(msg)):
                continue
        except Exception:
            pass

        r.raise_for_status()

    if last_err is not None:
        raise last_err
    raise ValueError(f"HuanAPI image 模型不可用: tried={candidates!r}")
