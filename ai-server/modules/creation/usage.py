"""创作子图的模型用量归一化与聚合。"""

from __future__ import annotations

from collections import OrderedDict
from typing import Any


def usage_item(raw: dict[str, Any] | None, stage: str) -> dict[str, Any]:
    """把不同厂商的用量字段统一为 Gateway 契约。"""
    source = raw if isinstance(raw, dict) else {}
    return {
        "stage": stage,
        "model_code": str(source.get("model_code") or source.get("model") or "").strip(),
        "input_tokens": _int_value(source.get("input_tokens")),
        "output_tokens": _int_value(source.get("output_tokens")),
        "image_count": _int_value(source.get("image_count") or source.get("images")),
        "estimated": bool(source.get("estimated", False)),
        "latency_ms": _int_value(source.get("latency_ms")),
    }


def aggregate_usage(items: list[dict[str, Any]] | None) -> dict[str, Any]:
    """保留逐调用明细，并按实际模型汇总供 Java 一次结算。"""
    normalized = [usage_item(item, str(item.get("stage") or "unknown")) for item in (items or [])]
    by_model: OrderedDict[str, dict[str, Any]] = OrderedDict()
    for item in normalized:
        model = item["model_code"] or "unknown"
        bucket = by_model.setdefault(model, {
            "model_code": model,
            "input_tokens": 0,
            "output_tokens": 0,
            "image_count": 0,
            "estimated": False,
            "latency_ms": 0,
        })
        bucket["input_tokens"] += item["input_tokens"]
        bucket["output_tokens"] += item["output_tokens"]
        bucket["image_count"] += item["image_count"]
        bucket["estimated"] = bucket["estimated"] or item["estimated"]
        bucket["latency_ms"] += item["latency_ms"]

    model_totals = list(by_model.values())
    final_model = normalized[-1]["model_code"] if normalized else ""
    return {
        "model_code": final_model,
        "input_tokens": sum(item["input_tokens"] for item in normalized),
        "output_tokens": sum(item["output_tokens"] for item in normalized),
        "image_count": sum(item["image_count"] for item in normalized),
        "estimated": any(item["estimated"] for item in normalized),
        "latency_ms": sum(item["latency_ms"] for item in normalized),
        "items": normalized,
        "model_totals": model_totals,
    }


def _int_value(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0
