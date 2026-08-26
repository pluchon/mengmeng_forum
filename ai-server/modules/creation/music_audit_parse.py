"""歌曲审核 LLM 响应 JSON 容错解析。"""

from __future__ import annotations

import json
import re
from typing import Any, TypeVar

from pydantic import BaseModel

ModelT = TypeVar("ModelT", bound=BaseModel)


def strip_llm_json(raw: str) -> str:
    text = str(raw or "").strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.IGNORECASE)
        text = re.sub(r"\s*```$", "", text.strip())
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        return text[start : end + 1]
    return text


def normalize_audit_payload(data: dict[str, Any]) -> dict[str, Any]:
    payload = dict(data)
    payload["reasons"] = _coerce_str_list(payload.get("reasons"))
    payload["moodTags"] = _coerce_str_list(payload.get("moodTags"))
    return payload


def parse_audit_model(raw: str, model_cls: type[ModelT]) -> ModelT:
    text = strip_llm_json(raw)
    data = json.loads(text)
    if not isinstance(data, dict):
        raise ValueError("审核响应不是 JSON 对象")
    return model_cls.model_validate(normalize_audit_payload(data))


def _coerce_str_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        text = value.strip()
        return [text[:120]] if text else []
    if isinstance(value, list):
        result: list[str] = []
        for item in value:
            text = str(item or "").strip()[:120]
            if text and text not in result:
                result.append(text)
            if len(result) >= 8:
                break
        return result
    return []
