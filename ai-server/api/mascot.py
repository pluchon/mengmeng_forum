"""
看板娘对话: POST /api/v1/mascot/chat

小项目: 单次 JSON 响应 (非 SSE). Java BFF 负责鉴权与配额.
"""
from __future__ import annotations

import logging
import re

from flask import jsonify, request

from api import api
from config import settings
from graphs.mascot_graph import run_mascot_chat

logger = logging.getLogger(__name__)


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
    skill = str(data.get("skill") or "writing").strip().lower()
    if skill not in ("writing", "help", "reading"):
        skill = "writing"
    tier = str(data.get("tier") or "basic").lower()
    if tier not in ("basic", "vip"):
        tier = "basic"

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
            }
        ),
        200,
    )
