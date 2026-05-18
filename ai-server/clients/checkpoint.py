"""
LangGraph PostgresSaver 工厂.
小项目并发低, 单实例共享一个 checkpointer 即可, 不做连接池.
"""
from __future__ import annotations

import logging
import threading

from langgraph.checkpoint.postgres import PostgresSaver
from psycopg import Connection

from config import settings

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_saver: PostgresSaver | None = None
_conn: Connection | None = None


def get_checkpointer() -> PostgresSaver:
    """第一次调用时建表 + 缓存; 之后调用直接返回单例"""
    global _saver, _conn
    if _saver is not None:
        return _saver
    with _lock:
        if _saver is not None:
            return _saver
        url = settings.pg_url()
        logger.info("[Checkpoint] 连接 Postgres: %s", _mask(url))
        _conn = Connection.connect(url, autocommit=True)
        _saver = PostgresSaver(_conn)
        if settings.postgres.get("auto_setup", True):
            _saver.setup()
            logger.info("[Checkpoint] PostgresSaver setup 完成")
    return _saver


def _mask(url: str) -> str:
    """日志里别打印明文密码"""
    if "@" not in url or "://" not in url:
        return url
    schema, rest = url.split("://", 1)
    if "@" not in rest:
        return url
    cred, host_part = rest.split("@", 1)
    return f"{schema}://***:***@{host_part}"
