"""从各厂商响应提取 token usage（OpenAI 兼容字段，非手算）。"""

from __future__ import annotations

import time
from typing import Any


def attach_latency(usage: dict[str, Any], started_at: float) -> dict[str, Any]:
    """在 usage 上附加服务端测得的 latency_ms。"""
    if not isinstance(usage, dict):
        usage = {}
    usage["latency_ms"] = max(0, int((time.perf_counter() - started_at) * 1000))
    return usage


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
    in_t = int(u.get("prompt_tokens") or u.get("input_tokens") or 0)
    out_t = int(u.get("completion_tokens") or u.get("output_tokens") or 0)
    has_tokens = in_t > 0 or out_t > 0
    return {
        "model_code": model_code,
        "input_tokens": in_t,
        "output_tokens": out_t,
        "images": 0,
        "estimated": estimated if estimated else not has_tokens,
    }
