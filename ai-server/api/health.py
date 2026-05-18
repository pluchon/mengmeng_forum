from __future__ import annotations

import logging

import redis
from flask import jsonify

from api import api
from clients.redis_client import redis_client

logger = logging.getLogger(__name__)


@api.route("/health", methods=["GET"])
def health():
    redis_ok = True
    try:
        redis_client.ping()
    except redis.RedisError:
        redis_ok = False
    return jsonify({
        "status": "ok",
        "service": "forum-ai-server",
        "redis": redis_ok,
    }), 200
