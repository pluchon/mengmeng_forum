"""
看板娘对话: POST /api/v1/mascot/chat

对话: POST /mascot/chat (JSON) 与 POST /mascot/chat/stream (SSE). Java BFF 负责鉴权与配额.
"""
from __future__ import annotations

import json
import logging
import re
from typing import Any

from flask import Response, jsonify, stream_with_context

from api import api
from api.common import RouteResponse, json_payload, mascot_auth_error
from config import settings
from graphs.mascot_graph import run_mascot_chat, stream_mascot_chat

logger = logging.getLogger(__name__)

_ALLOWED_SKILLS = {"writing", "help", "chat"}
_ALLOWED_TIERS = {"basic", "vip"}


def _client_datetime_from_body(data: dict[str, Any]) -> str:
    raw = data.get("client_datetime") or data.get("clientDatetime") or ""
    return str(raw).strip()[:64]


def _sanitize_appearance(raw: Any) -> str:
    # Java BFF 将 forum_mascot_model.code 放在 appearance；勿再强行收敛为固定枚举
    appearance = str(raw or "").strip().lower()
    appearance = re.sub(r"[^a-z0-9_-]", "", appearance)
    return (appearance or "snow_miku")[:64]


def _parse_vip_tier(raw: Any) -> int:
    try:
        return max(0, min(2, int(raw)))
    except (TypeError, ValueError):
        return 0


def _clean_history(raw: Any, max_len: int) -> list[dict[str, str]]:
    if not isinstance(raw, list):
        return []

    clean_history: list[dict[str, str]] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role", "")).lower()
        content = str(item.get("content", "")).strip()
        if role not in ("user", "assistant") or not content:
            continue
        clean_history.append({"role": role, "content": content[:max_len]})
    return clean_history


def _parse_mascot_payload(
    data: dict[str, Any],
) -> tuple[dict[str, Any] | None, RouteResponse | None]:
    message = str(data.get("message") or "").strip()
    if not message:
        return None, (jsonify({"code": 400, "msg": "message required"}), 400)

    max_len = int(settings.mascot.get("max_user_message_len", 2000))
    if len(message) > max_len:
        return None, (jsonify({"code": 400, "msg": f"message too long (max {max_len})"}), 400)

    skill = str(data.get("skill") or "chat").strip().lower()
    if skill not in _ALLOWED_SKILLS:
        skill = "chat"

    tier = str(data.get("tier") or "basic").lower()
    if tier not in _ALLOWED_TIERS:
        tier = "basic"

    return {
        "message": message,
        "session_id": str(data.get("session_id") or data.get("sessionId") or ""),
        "appearance": _sanitize_appearance(data.get("appearance")),
        "tier": tier,
        "history": _clean_history(data.get("history"), max_len),
        "llm_provider": str(data.get("llm_provider") or data.get("llmProvider") or "").strip(),
        "skill": skill,
        "vip_tier": _parse_vip_tier(data.get("vip_tier", data.get("vipTier", 0))),
        "client_datetime": _client_datetime_from_body(data),
    }, None


@api.route("/mascot/chat", methods=["POST"])
def mascot_chat() -> RouteResponse:
    auth_error = mascot_auth_error()
    if auth_error:
        return auth_error

    data = json_payload()
    payload, error = _parse_mascot_payload(data)
    if error:
        return error
    assert payload is not None

    try:
        result = run_mascot_chat(**payload)
    except Exception:
        logger.exception("mascot chat 异常")
        return jsonify({"code": 500, "msg": "mascot agent error"}), 500

    return (
        jsonify(
            {
                "code": 200,
                "msg": "ok",
                "reply": result.get("reply", ""),
                "live2d": result.get("live2d") or {},
                "suggested_appearance": result.get("suggested_appearance"),
                "usage": result.get("usage") or {},
                "mcp_used": bool(result.get("mcp_used")),
            }
        ),
        200,
    )


@api.route("/mascot/chat/stream", methods=["POST"])
def mascot_chat_stream() -> RouteResponse:
    auth_error = mascot_auth_error()
    if auth_error:
        return auth_error

    data = json_payload()
    payload, error = _parse_mascot_payload(data)
    if error:
        return error
    assert payload is not None

    def generate():
        try:
            for kind, event_payload in stream_mascot_chat(**payload):
                if kind == "text" and event_payload:
                    yield f"data: {json.dumps({'text': event_payload}, ensure_ascii=False)}\n\n"
                elif kind == "status" and event_payload:
                    yield f"data: {json.dumps({'meta': {'status': event_payload}}, ensure_ascii=False)}\n\n"
                elif kind == "meta" and event_payload:
                    yield f"data: {json.dumps({'meta': event_payload}, ensure_ascii=False)}\n\n"
                elif kind == "usage":
                    yield f"data: {json.dumps({'usage': event_payload}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
        except Exception:
            logger.exception("mascot chat stream 异常")
            err = json.dumps({"error": "mascot stream error"}, ensure_ascii=False)
            yield f"data: {err}\n\n"
            yield "data: [DONE]\n\n"

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
