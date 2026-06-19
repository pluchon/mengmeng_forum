from __future__ import annotations

from flask import jsonify

from api import api
from clients.redis_client import redis_ping


@api.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "service": "forum-ai-server",
        "redis": redis_ping(),
    }), 200
