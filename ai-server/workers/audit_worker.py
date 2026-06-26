"""
帖子审核后台 worker.

线程模型:
  Flask 主进程 + 1 个后台守护线程
   └─ BlockingConnection 订阅 q-audit-article
        └─ 拉到一条消息 -> 跑 LangGraph -> publish 到 forum.audit.result -> ack

幂等:
  - Redis SETNX f"{prefix}{taskId}" = "running"  TTL 1h
  - 跑完成功后改为 "done"
  - 若 SETNX 失败 (说明同 taskId 已在跑或已跑完), 直接 ack 并跳过

异常:
  - LangGraph 内部异常 -> 投递 AUDIT_ERROR 结果 -> ack
  - 投递失败 -> nack + requeue (false), 让任务进死信
"""
from __future__ import annotations

import json
import logging
import threading
import time

import redis

from clients.rabbit import audit_result_routing_key, audit_task_queue, open_consumer_channel, publish_json
from clients.redis_client import redis_client
from config import settings
from graphs.article_audit import run_audit

logger = logging.getLogger(__name__)

_AUDIT = settings.audit
_TASK_PREFIX = _AUDIT.get("task_state_key_prefix", "ai_audit:task_state:")
_TASK_TTL = int(_AUDIT.get("task_state_ttl", 3600))


def _key(task_id: str) -> str:
    return f"{_TASK_PREFIX}{task_id}"


def _try_lock(task_id: str) -> bool:
    """SETNX; 拿到锁返回 True"""
    try:
        return bool(redis_client.set(_key(task_id), "running", ex=_TASK_TTL, nx=True))
    except redis.RedisError:
        logger.exception("Redis SETNX 失败, 默认放行执行 task_id=%s", task_id)
        # Redis 挂了仍允许执行(由 Java 侧幂等兜底)
        return True


def _mark_done(task_id: str) -> None:
    try:
        redis_client.set(_key(task_id), "done", ex=86400)
    except redis.RedisError:
        logger.exception("更新 task_state -> done 失败 task_id=%s", task_id)


def _release_lock(task_id: str) -> None:
    """publish 失败时释放锁, 让后续重投可以再次执行"""
    try:
        redis_client.delete(_key(task_id))
    except redis.RedisError:
        logger.exception("释放 task_state 锁失败 task_id=%s (TTL 到期前同 taskId 重投会被挡)", task_id)


def _is_already_done(task_id: str) -> bool:
    try:
        v = redis_client.get(_key(task_id))
    except redis.RedisError:
        return False
    return v == "done"


def _handle_message(body: bytes) -> dict | None:
    """解析 + 跑图; 返回 publish 用的 result dict; 失败返回错误结果.

    幂等键 (task_state) 的生命周期:
      - 进入: SETNX 'running'  -> 拿到锁才能跑图
      - 跑图成功后: 仍保持 'running'; 由 _consume_loop 在 publish 成功后改为 'done'
      - 跑图异常: 仍返回 AUDIT_ERROR 结果, 由 publish 决定是否最终 done
      - publish 失败: _consume_loop 主动 _release_lock, 允许重投再跑
    """
    try:
        task = json.loads(body.decode("utf-8"))
    except Exception:
        logger.exception("[audit_worker] payload 解析失败")
        return None

    task_id = task.get("taskId")
    if not task_id:
        logger.warning("[audit_worker] payload 缺少 taskId, 丢弃")
        return None
    if _is_already_done(task_id):
        logger.info("[audit_worker] 任务已完成, 跳过 task_id=%s", task_id)
        return None
    if not _try_lock(task_id):
        logger.info("[audit_worker] 任务已在执行中, 跳过 task_id=%s", task_id)
        return None

    logger.info("[audit_worker] 开始执行审核 task_id=%s articleId=%s",
                task_id, task.get("articleId"))
    t0 = time.time()
    try:
        result = run_audit(task)
        logger.info("[audit_worker] 审核完成 task_id=%s status=%s cost=%.1fs",
                    task_id, result.get("finalStatus"), time.time() - t0)
        return result
    except Exception:
        logger.exception("[audit_worker] 审核异常 task_id=%s", task_id)
        return {
            "taskId": task_id,
            "articleId": task.get("articleId"),
            "userId": task.get("userId"),
            "finalStatus": "AUDIT_ERROR",
            "finalReason": "审核服务运行时异常",
            "title": task.get("title", ""),
            "summary": "",
            "finishedAt": int(time.time() * 1000),
        }


def _consume_loop() -> None:
    """blocking 消费循环; 出错就 sleep 5s 重连.

    publish 成功 -> 标记 done + ack
    publish 失败 -> 释放 task_state 锁 + nack(进死信), 后续可手动重投
    业务异常 (handle 抛错) -> 同样释放锁 + nack
    """
    queue = audit_task_queue()
    result_rk = audit_result_routing_key()
    while True:
        try:
            conn, ch = open_consumer_channel()
            logger.info("[audit_worker] 已连接, 订阅队列=%s", queue)
            for method_frame, _props, body in ch.consume(queue, auto_ack=False, inactivity_timeout=None):
                if method_frame is None:
                    continue
                task_id_for_lock = None
                try:
                    # 先把 taskId 探出来, 后续异常路径要释放锁
                    try:
                        task_id_for_lock = (json.loads(body.decode("utf-8")) or {}).get("taskId")
                    except Exception:
                        task_id_for_lock = None

                    result = _handle_message(body)
                    if result is None:
                        # 消息格式无效 / 已处理 / 已被其他 worker 锁: 直接 ack
                        ch.basic_ack(delivery_tag=method_frame.delivery_tag)
                        continue
                    ok = publish_json(result_rk, result)
                    if not ok:
                        # publish 失败: 释放锁, 让重投有机会; 这条消息进死信
                        if task_id_for_lock:
                            _release_lock(task_id_for_lock)
                        ch.basic_nack(delivery_tag=method_frame.delivery_tag, requeue=False)
                        logger.error("[audit_worker] publish result 失败 -> 死信 task_id=%s", task_id_for_lock)
                        continue
                    # publish 成功后才 done; 重投会被 _is_already_done 直接跳过
                    if task_id_for_lock:
                        _mark_done(task_id_for_lock)
                    ch.basic_ack(delivery_tag=method_frame.delivery_tag)
                except Exception:
                    logger.exception("[audit_worker] 消息处理异常 -> 死信")
                    if task_id_for_lock:
                        _release_lock(task_id_for_lock)
                    try:
                        ch.basic_nack(delivery_tag=method_frame.delivery_tag, requeue=False)
                    except Exception:
                        logger.exception("[audit_worker] basic_nack 失败")
        except Exception:
            logger.exception("[audit_worker] 消费连接断开, 5s 后重连")
            time.sleep(5)


def start_in_background() -> threading.Thread:
    t = threading.Thread(target=_consume_loop, name="audit-worker", daemon=True)
    t.start()
    logger.info("[audit_worker] 后台线程已启动")
    return t
