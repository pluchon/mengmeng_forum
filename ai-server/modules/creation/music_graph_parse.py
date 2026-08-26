"""配乐推荐/搜索 LangGraph LLM 响应 JSON 容错解析。"""

from __future__ import annotations

import json
from typing import Any

from modules.creation.music_audit_parse import strip_llm_json


def normalize_search_intent(data: dict[str, Any]) -> dict[str, Any]:
    payload = dict(data)
    payload["moods"] = _coerce_str_list(payload.get("moods"))
    payload["keywords"] = _coerce_str_list(payload.get("keywords"))
    payload["genre"] = _coerce_optional_str(payload.get("genre"), max_len=40)
    payload["artist"] = _coerce_optional_str(payload.get("artist"), max_len=80)
    payload["summary"] = str(payload.get("summary") or "").strip()[:200]
    return payload


def normalize_music_selection(data: dict[str, Any]) -> dict[str, Any]:
    tracks_raw = data.get("tracks") or []
    tracks: list[dict[str, Any]] = []
    if isinstance(tracks_raw, list):
        for item in tracks_raw:
            if isinstance(item, dict):
                tracks.append(normalize_ranked_track(item))
            if len(tracks) >= 12:
                break
    return {"tracks": tracks}


def normalize_ranked_track(item: dict[str, Any]) -> dict[str, Any]:
    row = dict(item)
    row["musicKey"] = str(row.get("musicKey") or row.get("music_key") or "").strip()[:128]
    row["reason"] = str(row.get("reason") or "").strip()[:80]
    row["score"] = normalize_score(row.get("score"))
    return row


def normalize_score(value: Any) -> float:
    try:
        score = float(value)
    except (TypeError, ValueError):
        return 0.0
    if score > 1.0:
        if score <= 100.0:
            score /= 100.0
        else:
            score = 1.0
    return max(0.0, min(1.0, score))


def parse_search_intent_payload(raw: str) -> dict[str, Any]:
    text = strip_llm_json(raw)
    data = json.loads(text)
    if not isinstance(data, dict):
        raise ValueError("搜索意图不是 JSON 对象")
    return normalize_search_intent(data)


def parse_music_selection_payload(payload: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("选曲结果不是 JSON 对象")
    return normalize_music_selection(payload)


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


def _coerce_optional_str(value: Any, *, max_len: int) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        parts = _coerce_str_list(value)
        return ", ".join(parts)[:max_len]
    text = str(value).strip()
    return text[:max_len] if text else ""
