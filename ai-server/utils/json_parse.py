"""从 LLM 文本中解析 JSON 对象的轻量工具。"""

from __future__ import annotations

import json
import re
from typing import Any, TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)

_FENCE_RE = re.compile(r"^```(?:json)?\s*|\s*```$", re.IGNORECASE)
_OBJECT_RE = re.compile(r"\{[\s\S]*\}")


def parse_json_object(text: str | None) -> dict[str, Any] | None:
    """剥离 markdown fence 后解析首个 JSON 对象；失败返回 None。"""
    raw = (text or "").strip()
    if not raw:
        return None
    cleaned = _FENCE_RE.sub("", raw).strip()
    candidate = cleaned
    match = _OBJECT_RE.search(cleaned)
    if match:
        candidate = match.group(0)
    try:
        data = json.loads(candidate)
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def safe_validate_json(model_cls: type[T], raw: str | None) -> T | None:
    """loads + model_validate；失败返回 None，由调用方决定 fallback。"""
    try:
        return model_cls.model_validate_json(raw or "{}")
    except Exception:
        data = parse_json_object(raw)
        if data is None:
            return None
        try:
            return model_cls.model_validate(data)
        except Exception:
            return None
