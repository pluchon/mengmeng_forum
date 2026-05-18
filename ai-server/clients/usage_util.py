"""从各厂商响应提取 token usage."""

from __future__ import annotations

from typing import Any


def usage_from_openai_style(data: dict[str, Any], model_code: str, *, estimated: bool = False) -> dict[str, Any]:
    u = data.get("usage") if isinstance(data, dict) else None
    if not isinstance(u, dict):
        return {
            "model_code": model_code,
            "input_tokens": 0,
            "output_tokens": 0,
            "images": 0,
            "estimated": True,
        }
    return {
        "model_code": model_code,
        "input_tokens": int(u.get("prompt_tokens") or u.get("input_tokens") or 0),
        "output_tokens": int(u.get("completion_tokens") or u.get("output_tokens") or 0),
        "images": 0,
        "estimated": estimated,
    }
