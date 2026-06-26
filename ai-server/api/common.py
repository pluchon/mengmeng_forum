"""Flask 路由层共用工具（参数读取、内网鉴权）."""

from __future__ import annotations

from typing import Any

from flask import Response, jsonify, request

from config import settings
from security.internal_auth import internal_auth_ok

RouteResponse = Response | tuple[Response, int]


def json_payload() -> dict[str, Any]:
    data = request.get_json(silent=True) or {}
    return data if isinstance(data, dict) else {}


def check_internal_auth(expected_key: str) -> RouteResponse | None:
    """内网密钥校验失败时返回 403 响应，通过则返回 None."""
    if internal_auth_ok(expected_key):
        return None
    return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403


def ai_hub_auth_error() -> RouteResponse | None:
    key = (settings.ai_hub.get("internal_key") or "").strip()
    return check_internal_auth(key)


def mascot_auth_error() -> RouteResponse | None:
    key = (settings.mascot.get("internal_key") or "").strip()
    return check_internal_auth(key)
