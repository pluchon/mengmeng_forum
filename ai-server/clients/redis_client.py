"""
全局 Redis 客户端单例.
"""
from __future__ import annotations

import logging

import redis

from config import settings

logger = logging.getLogger(__name__)

_cfg = settings.redis

redis_client = redis.StrictRedis(
    host=_cfg.get("host", "localhost"),
    port=int(_cfg.get("port", 6379)),
    password=_cfg.get("password"),
    db=int(_cfg.get("db", 0)),
    decode_responses=True,
    socket_timeout=int(_cfg.get("socket_timeout", 2)),
    socket_connect_timeout=int(_cfg.get("socket_timeout", 2)),
)


def redis_ping() -> bool:
    """健康检查专用探测；路由层不直接接触 Redis SDK."""
    try:
        return bool(redis_client.ping())
    except redis.RedisError:
        logger.warning("Redis health check failed")
        return False
