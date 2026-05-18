"""
全局 Redis 客户端单例.
"""
from __future__ import annotations

import redis

from config import settings

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
