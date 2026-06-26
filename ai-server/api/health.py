from __future__ import annotations

from flask import Response, jsonify

from api import api
from api.common import RouteResponse
from clients.redis_client import redis_ping


@api.route("/health", methods=["GET"])
def health() -> RouteResponse:
    return jsonify({
        "status": "ok",
        "service": "forum-ai-server",
        "redis": redis_ping(),
    }), 200
