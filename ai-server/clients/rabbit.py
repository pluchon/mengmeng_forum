"""
RabbitMQ 连接管理.
- 消费侧: BlockingConnection + 后台线程; 简单可靠, 小项目并发量低足够
- 发布侧: 单独连接, 复用线程安全 (单线程发布), 不与消费者竞争 channel
"""
from __future__ import annotations

import json
import logging
import threading
import time

import pika
from pika.adapters.blocking_connection import BlockingConnection

from config import settings

from utils.trace import MQ_TRACE_HEADER, get_trace_id

logger = logging.getLogger(__name__)

_cfg = settings.rabbitmq
_PUBLISH_LOCK = threading.Lock()
_publisher_connection: BlockingConnection | None = None
_publisher_channel = None


def _params() -> pika.ConnectionParameters:
    creds = pika.PlainCredentials(
        _cfg.get("username", "guest"),
        _cfg.get("password", "guest"),
    )
    return pika.ConnectionParameters(
        host=_cfg.get("host", "localhost"),
        port=int(_cfg.get("port", 5672)),
        virtual_host=_cfg.get("virtual_host", "/"),
        credentials=creds,
        heartbeat=60,
        blocked_connection_timeout=30,
    )


def consumer_worker_threads() -> int:
    """异步任务消费线程数，至少 1"""
    return max(1, int(_cfg.get("worker_threads", 4)))


def open_consumer_channel() -> tuple[BlockingConnection, pika.channel.Channel]:
    """消费者独占一个 BlockingConnection + Channel。

    prefetch 取线程数：未确认消息数就是天然的背压，
    小于线程数会让线程闲着，大于线程数则任务会堆在进程内存里。
    """
    conn = BlockingConnection(_params())
    ch = conn.channel()
    ch.basic_qos(prefetch_count=int(_cfg.get("prefetch", consumer_worker_threads())))
    return conn, ch


def _ensure_publisher() -> pika.channel.Channel:
    global _publisher_connection, _publisher_channel
    connection_unavailable = _publisher_connection is None or _publisher_connection.is_closed
    channel_unavailable = _publisher_channel is None or _publisher_channel.is_closed
    if connection_unavailable or channel_unavailable:
        _reset_publisher()
        _publisher_connection = BlockingConnection(_params())
        _publisher_channel = _publisher_connection.channel()
        _publisher_channel.confirm_delivery()
        logger.info("[Rabbit] 发布连接已建立")
    return _publisher_channel


def _reset_publisher() -> None:
    global _publisher_connection, _publisher_channel
    connection = _publisher_connection
    _publisher_connection = None
    _publisher_channel = None
    if connection is None or connection.is_closed:
        return
    try:
        connection.close()
    except Exception as exc:
        logger.debug("[Rabbit] 关闭失效发布连接失败: %s", exc)


def publish_json(routing_key: str, payload: dict, retries: int = 3) -> bool:
    """
    线程安全的 JSON 发布; 失败返回 False, 调用方可决定重试.
    使用 confirm_delivery 模式确保到达 broker.
    """
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    last_err = None
    for attempt in range(max(1, retries)):
        with _PUBLISH_LOCK:
            try:
                ch = _ensure_publisher()
                ch.basic_publish(
                    exchange=_cfg.get("exchange", "t_exchange_1"),
                    routing_key=routing_key,
                    body=body,
                    properties=pika.BasicProperties(
                        content_type="application/json",
                        delivery_mode=2,  # persistent
                        # 把 traceId 原样带回 Java：审核结果是回投给 content 域消费的，
                        # 这里不带的话链路会断在最后一跳，结果日志和触发它的请求对不上
                        headers={MQ_TRACE_HEADER: get_trace_id()},
                    ),
                    mandatory=True,
                )
                return True
            except Exception as exc:
                last_err = exc
                logger.warning(
                    "[Rabbit] 发布失败 routing_key=%s attempt=%s/%s: %s",
                    routing_key,
                    attempt + 1,
                    retries,
                    exc,
                )
                _reset_publisher()
        if attempt + 1 < retries:
            time.sleep(0.35 * (attempt + 1))
    logger.exception("[Rabbit] 发布最终失败 routing_key=%s", routing_key, exc_info=last_err)
    return False


def audit_task_queue() -> str:
    return _cfg.get("audit_task_queue", "q-audit-article")


def audit_result_routing_key() -> str:
    return _cfg.get("audit_result_routing_key", "forum.audit.result")


def ai_async_task_queue() -> str:
    return _cfg.get("ai_async_task_queue", "q-ai-async-task")


def ai_content_result_routing_key() -> str:
    return _cfg.get("ai_content_result_routing_key", "forum.ai.content.result")


def ai_im_result_routing_key() -> str:
    return _cfg.get("ai_im_result_routing_key", "forum.ai.im.result")
