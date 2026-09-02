"""
LangGraph PostgresSaver 工厂.

原来是单个 psycopg Connection 跨线程共享（注释写着「小项目并发低不做连接池」）。
但审核 worker 本身就是多线程的（rabbitmq.worker_threads=4），两个任务撞在一起
就会在同一条连接上交错。换成 ConnectionPool——psycopg 官方给并发场景的做法，
PostgresSaver 直接支持传池。
"""
from __future__ import annotations

import atexit
import logging
import threading

from langgraph.checkpoint.postgres import PostgresSaver
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from config import settings

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_saver: PostgresSaver | None = None
_pool: ConnectionPool | None = None


def get_checkpointer() -> PostgresSaver:
    """第一次调用时建表 + 缓存; 之后调用直接返回单例"""
    global _saver, _pool
    if _saver is not None:
        return _saver
    with _lock:
        if _saver is not None:
            return _saver
        url = settings.pg_url()
        logger.info("[Checkpoint] 连接 Postgres: %s", _mask(url))
        # PostgresSaver 要求连接是 autocommit + dict_row
        _pool = ConnectionPool(
            conninfo=url,
            min_size=1,
            max_size=int(settings.postgres.get("pool_max_size", 8)),
            kwargs={"autocommit": True, "row_factory": dict_row},
            open=True,
        )
        _saver = PostgresSaver(_pool)
        # 池自带后台线程；不显式关的话，解释器终结阶段 __del__ 去 join 会抛一串
        # PythonFinalizationError，日志里很难看
        atexit.register(close_checkpointer)
        if settings.postgres.get("auto_setup", True):
            _saver.setup()
            logger.info("[Checkpoint] PostgresSaver setup 完成")
    return _saver


def close_checkpointer() -> None:
    """进程退出时收掉连接池。重复调用安全。"""
    global _saver, _pool
    pool, _pool, _saver = _pool, None, None
    if pool is None:
        return
    try:
        pool.close()
    except Exception:
        logger.warning("[Checkpoint] 连接池关闭失败", exc_info=True)


def _mask(url: str) -> str:
    """日志里别打印明文密码"""
    if "@" not in url or "://" not in url:
        return url
    schema, rest = url.split("://", 1)
    if "@" not in rest:
        return url
    cred, host_part = rest.split("@", 1)
    return f"{schema}://***:***@{host_part}"
