"""
AI Hub: /api/v1/ai/write | cover-hints | image
"""

from __future__ import annotations

import logging
import time
from typing import Any

from flask import jsonify

from api import api
from api.common import RouteResponse, ai_hub_auth_error, json_payload
from clients.usage_util import attach_latency
from services.ai_hub_service import (
    AiHubConfigError,
    generate_cover_hints,
    generate_gobang_move,
    generate_image,
    generate_jinzi_move,
    generate_write_content,
)

logger = logging.getLogger(__name__)


def _clean_messages(raw: Any, max_turns: int = 24) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    if not isinstance(raw, list):
        return out
    for item in raw[-max_turns:]:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role", "")).strip().lower()
        content = str(item.get("content", "")).strip()
        if role not in ("system", "user", "assistant") or not content:
            continue
        out.append({"role": role, "content": content[:32000]})
    return out


@api.route("/ai/write", methods=["POST"])
def ai_write() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    data = json_payload()
    kind = str(data.get("kind") or "").strip().lower()
    messages = _clean_messages(data.get("messages"))
    if not kind or not messages:
        return jsonify({"code": 400, "msg": "kind and messages required"}), 400

    t0 = time.perf_counter()
    try:
        content, usage = generate_write_content(kind, messages)
    except Exception:
        logger.exception("ai_write 失败 kind=%s", kind)
        return jsonify({"code": 500, "msg": "ai write failed"}), 500
    return jsonify({"code": 200, "msg": "ok", "data": {"content": content, "usage": attach_latency(usage, t0)}})


@api.route("/ai/cover-hints", methods=["POST"])
def ai_cover_hints() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    data = json_payload()
    article = str(data.get("article_text") or "").strip()
    if not article:
        return jsonify({"code": 400, "msg": "article_text required"}), 400
    try:
        content, usage = generate_cover_hints(article)
    except Exception:
        logger.exception("cover-hints 失败")
        return jsonify({"code": 500, "msg": "cover hints failed"}), 500
    return jsonify({"code": 200, "msg": "ok", "data": {"content": content, "usage": usage}})


@api.route("/ai/gobang-move", methods=["POST"])
def ai_gobang_move() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    data = json_payload()
    board = data.get("board")
    try:
        ai_chess = int(data.get("ai_chess") or 2)
    except (TypeError, ValueError):
        ai_chess = 2
    if ai_chess not in (1, 2):
        ai_chess = 2
    model_code = str(data.get("model_code") or "").strip()
    use_llm = bool(data.get("use_llm", True))

    t0 = time.perf_counter()
    try:
        move = generate_gobang_move(board, ai_chess, model_code, use_llm=use_llm)
    except ValueError as exc:
        return jsonify({"code": 400, "msg": str(exc)}), 400
    except Exception:
        logger.exception("gobang-move 失败")
        return jsonify({"code": 503, "msg": "gobang ai unavailable"}), 503
    usage = attach_latency(move.get("usage") or {}, t0)
    return jsonify({
        "code": 200,
        "msg": "ok",
        "data": {
            "row": move["row"],
            "col": move["col"],
            "model": move["model"],
            "modelCode": move.get("model") or move.get("model_name"),
            "modelName": move.get("model_name") or move["model"],
            "modelVersion": move.get("model_version") or move["model"],
            "strategyName": move.get("strategy_name") or "llm_with_rule_guard",
            "fallback": bool(move.get("fallback")),
            "usage": usage,
        },
    })


@api.route("/ai/jinzi-move", methods=["POST"])
def ai_jinzi_move() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    data = json_payload()
    board = data.get("board")
    try:
        ai_chess = int(data.get("ai_chess") or 2)
    except (TypeError, ValueError):
        ai_chess = 2
    if ai_chess not in (1, 2):
        ai_chess = 2
    model_code = str(data.get("model_code") or "").strip()
    use_llm = bool(data.get("use_llm", False))

    t0 = time.perf_counter()
    try:
        move = generate_jinzi_move(board, ai_chess, model_code, use_llm=use_llm)
    except ValueError as exc:
        return jsonify({"code": 400, "msg": str(exc)}), 400
    except Exception:
        logger.exception("jinzi-move 失败")
        return jsonify({"code": 503, "msg": "jinzi ai unavailable"}), 503
    usage = attach_latency(move.get("usage") or {}, t0)
    return jsonify({
        "code": 200,
        "msg": "ok",
        "data": {
            "row": move["row"],
            "col": move["col"],
            "model": move["model"],
            "modelCode": move.get("model") or move.get("model_name"),
            "modelName": move.get("model_name") or move["model"],
            "modelVersion": move.get("model_version") or move["model"],
            "strategyName": move.get("strategy_name") or "llm_with_rule_guard",
            "fallback": bool(move.get("fallback")),
            "usage": usage,
        },
    })


@api.route("/ai/image", methods=["POST"])
def ai_image() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    data = json_payload()
    prompt = str(data.get("prompt") or "").strip()
    quality = str(data.get("quality") or "normal").strip().lower()
    if not prompt:
        return jsonify({"code": 400, "msg": "prompt required"}), 400
    if quality not in ("normal", "premium"):
        return jsonify({"code": 400, "msg": "quality must be normal|premium"}), 400

    t0 = time.perf_counter()
    try:
        url, usage, mcp_used = generate_image(prompt, quality)
    except AiHubConfigError as exc:
        return jsonify({"code": 503, "msg": str(exc)}), 503
    except Exception as exc:
        logger.exception("ai_image 失败 quality=%s", quality)
        msg = "image generation failed"
        if quality == "premium":
            detail = str(exc).strip()
            if "SSLError" in detail or "SSL" in detail:
                msg = (
                    "无法连接 HuanAPI（SSL/网络异常），请检查本机网络、代理或防火墙；"
                    "可在环境变量 HUANAPI_BASE_URL 指定网关地址"
                )
        return jsonify({"code": 500, "msg": msg}), 500
    return jsonify({
        "code": 200,
        "msg": "ok",
        "data": {"url": url, "usage": attach_latency(usage, t0), "mcp_used": mcp_used},
    })
