"""
看板娘对话: POST /api/v1/mascot/chat

对话: POST /mascot/chat (JSON) 与 POST /mascot/chat/stream (SSE). Java BFF 负责鉴权与配额.
"""
from __future__ import annotations

import logging
import re

import json

from flask import Response, jsonify, request, stream_with_context

from api import api
from config import settings
from graphs.mascot_graph import run_mascot_chat, stream_mascot_chat

logger = logging.getLogger(__name__)


def _client_datetime_from_body(data: dict) -> str:
    raw = data.get("client_datetime") or data.get("clientDatetime") or ""
    return str(raw).strip()[:64]


def _internal_auth_ok() -> bool:
    expected = (settings.mascot.get("internal_key") or "").strip()
    if not expected:
        return True
    got = (request.headers.get("X-Internal-Key") or "").strip()
    return got == expected


@api.route("/mascot/chat", methods=["POST"])
def mascot_chat():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403

    data = request.get_json(silent=True) or {}
    message = (data.get("message") or "").strip()
    if not message:
        return jsonify({"code": 400, "msg": "message required"}), 400

    max_len = int(settings.mascot.get("max_user_message_len", 2000))
    if len(message) > max_len:
        return jsonify({"code": 400, "msg": f"message too long (max {max_len})"}), 400

    session_id = str(data.get("session_id") or data.get("sessionId") or "")
    # Java BFF 将 forum_mascot_model.code 放在 appearance；勿再强行收敛为 standard/keyboard/gamepad
    appearance = str(data.get("appearance") or "").strip().lower()
    appearance = re.sub(r"[^a-z0-9_-]", "", appearance)
    if not appearance:
        appearance = "snow_miku"
    appearance = appearance[:64]

    llm_provider = str(data.get("llm_provider") or data.get("llmProvider") or "").strip()
    skill = str(data.get("skill") or "chat").strip().lower()
    if skill not in ("writing", "help", "chat"):
        skill = "chat"
    tier = str(data.get("tier") or "basic").lower()
    if tier not in ("basic", "vip"):
        tier = "basic"
    vip_tier_raw = data.get("vip_tier", data.get("vipTier", 0))
    try:
        vip_tier = max(0, min(2, int(vip_tier_raw)))
    except (TypeError, ValueError):
        vip_tier = 0

    history = data.get("history")
    if not isinstance(history, list):
        history = []
    clean_history: list[dict[str, str]] = []
    for item in history:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role", "")).lower()
        content = str(item.get("content", "")).strip()
        if role not in ("user", "assistant") or not content:
            continue
        clean_history.append({"role": role, "content": content[:max_len]})

    try:
        result = run_mascot_chat(
            message=message,
            session_id=session_id,
            appearance=appearance,
            tier=tier,
            history=clean_history,
            llm_provider=llm_provider,
            skill=skill,
            vip_tier=vip_tier,
            client_datetime=_client_datetime_from_body(data),
        )
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
def mascot_chat_stream():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403

    data = request.get_json(silent=True) or {}
    message = (data.get("message") or "").strip()
    if not message:
        return jsonify({"code": 400, "msg": "message required"}), 400

    max_len = int(settings.mascot.get("max_user_message_len", 2000))
    if len(message) > max_len:
        return jsonify({"code": 400, "msg": f"message too long (max {max_len})"}), 400

    session_id = str(data.get("session_id") or data.get("sessionId") or "")
    appearance = str(data.get("appearance") or "").strip().lower()
    appearance = re.sub(r"[^a-z0-9_-]", "", appearance)
    if not appearance:
        appearance = "snow_miku"
    appearance = appearance[:64]

    llm_provider = str(data.get("llm_provider") or data.get("llmProvider") or "").strip()
    skill = str(data.get("skill") or "chat").strip().lower()
    if skill not in ("writing", "help", "chat"):
        skill = "chat"
    tier = str(data.get("tier") or "basic").lower()
    if tier not in ("basic", "vip"):
        tier = "basic"
    vip_tier_raw = data.get("vip_tier", data.get("vipTier", 0))
    try:
        vip_tier = max(0, min(2, int(vip_tier_raw)))
    except (TypeError, ValueError):
        vip_tier = 0

    history = data.get("history")
    if not isinstance(history, list):
        history = []
    clean_history: list[dict[str, str]] = []
    for item in history:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role", "")).lower()
        content = str(item.get("content", "")).strip()
        if role not in ("user", "assistant") or not content:
            continue
        clean_history.append({"role": role, "content": content[:max_len]})

    def generate():
        try:
            for kind, payload in stream_mascot_chat(
                message=message,
                session_id=session_id,
                appearance=appearance,
                tier=tier,
                history=clean_history,
                llm_provider=llm_provider,
                skill=skill,
                vip_tier=vip_tier,
                client_datetime=_client_datetime_from_body(data),
            ):
                if kind == "text" and payload:
                    yield f"data: {json.dumps({'text': payload}, ensure_ascii=False)}\n\n"
                elif kind == "usage":
                    yield f"data: {json.dumps({'usage': payload}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
        except Exception as ex:
            logger.exception("mascot chat stream 异常")
            msg = str(ex).strip()[:400] or "mascot stream error"
            err = json.dumps({"error": msg}, ensure_ascii=False)
            yield f"data: {err}\n\n"
            yield "data: [DONE]\n\n"

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
