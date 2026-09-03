from __future__ import annotations

import logging
import uuid
from contextvars import ContextVar

# Java 侧全链路用的都是这个头，跨语言保持同名
TRACE_HEADER = "X-Trace-Id"

# Java 侧发消息时写进 RabbitMQ 消息头的字段名，与 ForumMqTrace.TRACE_HEADER 一致
MQ_TRACE_HEADER = "X-Trace-Id"

_NO_TRACE = "no-trace"

# contextvar 而不是 threading.local：worker 用线程池跑任务，
# 而 Flask 侧未来若改异步也不用再换一次实现
_current_trace_id: ContextVar[str] = ContextVar("forum_trace_id", default=_NO_TRACE)


def set_trace_id(trace_id: str | None) -> str:
    """设置当前上下文的 traceId，空值则现生成一个并返回实际使用的值。"""
    value = (trace_id or "").strip()
    if not value:
        value = "py-" + uuid.uuid4().hex[:16]
    _current_trace_id.set(value)
    return value


def get_trace_id() -> str:
    return _current_trace_id.get()


def trace_id_from_headers(headers: object) -> str | None:
    """从 HTTP header 或 RabbitMQ 消息头里取 traceId，两种都是普通映射。"""
    if not headers:
        return None
    getter = getattr(headers, "get", None)
    if getter is None:
        return None
    for key in (TRACE_HEADER, TRACE_HEADER.lower(), "traceId", "trace_id"):
        value = getter(key)
        if value:
            return str(value)
    return None


class TraceIdFilter(logging.Filter):
    """把 traceId 注入每条日志记录，让 formatter 里的 %(trace_id)s 有值。

    用 Filter 而不是让每处调用自己拼字符串：日志散在几十个模块里，
    漏一处就断一处，而漏掉的那处往往正是出问题的地方。
    """

    def filter(self, record: logging.LogRecord) -> bool:
        record.trace_id = get_trace_id()
        return True
