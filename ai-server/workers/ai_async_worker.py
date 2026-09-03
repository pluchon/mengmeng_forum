"""帖子总结与文本审核通用异步worker。"""

from __future__ import annotations

import asyncio
import json
import logging
import threading
from concurrent.futures import ThreadPoolExecutor
import time
from typing import Any

import redis

from clients.rabbit import (
    ai_async_task_queue,
    ai_content_result_routing_key,
    ai_im_result_routing_key,
    consumer_worker_threads,
    open_consumer_channel,
    publish_json,
)
from clients.redis_client import redis_client
from utils.trace import set_trace_id, trace_id_from_headers
from modules.moderation import ContentModerationModule
from modules.summary.graph import run_summary_graph
from modules.creation.music_audit_audio import audit_music_audio
from modules.creation.music_audit_text import audit_music_text
from modules.creation.usage import aggregate_usage
from rag.music_indexer import index_music_track
from runtime.contracts import ModuleRequest

logger = logging.getLogger(__name__)
_TASK_PREFIX = "ai_async:task_state:"
# 锁只需要覆盖"单个任务的最长耗时"，取 10 分钟。
# 之前是 3600：进程被杀导致 _release 没执行时，锁会残留一小时，
# 而 Java 侧每 60 秒补投一次，每次都撞上陈旧锁被静默 ack 丢弃，
# 直到 retry_count 用尽，帖子永远停在 SUMMARY_PROCESSING
_TASK_TTL = 600


def _task_key(task_id: str) -> str:
    return f"{_TASK_PREFIX}{task_id}"


def _try_lock(task_id: str) -> bool:
    try:
        acquired = bool(redis_client.set(_task_key(task_id), "running", ex=_TASK_TTL, nx=True))
        if not acquired:
            # 正常去重也会走到这里，但陈旧锁同样走这里。没有日志的话，
            # "任务被静默丢弃"从外面完全看不出来
            state = redis_client.get(_task_key(task_id))
            ttl = redis_client.ttl(_task_key(task_id))
            logger.info(
                "[ai_async_worker] 跳过重复任务 task_id=%s state=%s ttl=%s",
                task_id, state, ttl,
            )
        return acquired
    except redis.RedisError:
        logger.exception("AI异步任务锁不可用，交由Java结果幂等兜底 task_id=%s", task_id)
        return True


def _mark_done(task_id: str) -> None:
    try:
        redis_client.set(_task_key(task_id), "done", ex=86400)
    except redis.RedisError:
        logger.exception("AI异步任务完成标记失败 task_id=%s", task_id)


def _release(task_id: str) -> None:
    try:
        redis_client.delete(_task_key(task_id))
    except redis.RedisError:
        logger.exception("AI异步任务锁释放失败 task_id=%s", task_id)


def _execute(task: dict[str, Any]) -> dict[str, Any]:
    task_id = str(task.get("taskId") or "").strip()
    task_type = str(task.get("taskType") or "").strip().upper()
    base: dict[str, Any] = {
        "taskId": task_id,
        "taskType": task_type,
        "targetType": task.get("targetType"),
        "targetId": task.get("targetId"),
        "contentHash": task.get("contentHash"),
        "resultDomain": str(task.get("resultDomain") or "CONTENT").upper(),
        "finishedAt": int(time.time() * 1000),
    }
    try:
        if task_type == "ARTICLE_SUMMARY":
            result = run_summary_graph(
                str(task.get("title") or ""),
                str(task.get("content") or ""),
            )
            return {
                **base,
                "finalStatus": "READY",
                "summary": result["summary"],
                "usage": result.get("usage") or {},
                "route": result.get("route"),
                "deepUsed": result.get("deepUsed", False),
                "mcpUsed": result.get("mcpUsed", False),
            }
        if task_type in {
            "COMMENT_AUTO_MODERATION",
            "DANMAKU_AUTO_MODERATION",
            "CONTENT_REPORT_MODERATION",
            "CHAT_REPORT_MODERATION",
        }:
            request = ModuleRequest(
                task_type="CONTENT_MODERATION",
                intent="TEXT_AUDIT",
                version="v1",
                request_id=task_id,
                trace_id=task_id,
                user_context={},
                payload={
                    "title": task.get("title") or "",
                    "content": task.get("content") or "",
                    # 举报理由只在举报类任务里有值，自动审核类任务为空
                    "reportReason": task.get("reportReason") or "",
                },
            )
            result = asyncio.run(ContentModerationModule().run(request))
            allowed = bool(result.data.get("allowed"))
            return {
                **base,
                "finalStatus": "COMPLIANT" if allowed else "VIOLATION",
                "finalReason": str(result.data.get("reason") or ""),
            }
        if task_type == "USER_MUSIC_ANALYZE":
            logger.warning(
                "[USER_MUSIC_ANALYZE] 收到任务 task_id=%s target_id=%s",
                task_id,
                task.get("targetId"),
            )
            result = _execute_user_music_analyze(task, base)
            logger.warning(
                "[USER_MUSIC_ANALYZE] 任务完成 task_id=%s finalStatus=%s",
                task_id,
                result.get("finalStatus"),
            )
            return result
        raise ValueError(f"不支持的AI异步任务类型: {task_type}")
    except Exception as exc:
        logger.exception("AI异步任务执行失败 task_id=%s task_type=%s", task_id, task_type)
        return {**base, "finalStatus": "ERROR", "finalReason": str(exc)[:300]}


_RISK_ORDER = {"low": 0, "medium": 1, "high": 2}


def _execute_user_music_analyze(task: dict[str, Any], base: dict[str, Any]) -> dict[str, Any]:
    title = str(task.get("title") or "").strip()
    artist = str(task.get("artist") or "").strip()
    lyric_text = str(task.get("lyricText") or task.get("lyric_text") or "").strip()
    audio_url = str(task.get("audioUrl") or task.get("audio_url") or "").strip()
    user_mood_tags = task.get("userMoodTags") or task.get("user_mood_tags") or []
    if not isinstance(user_mood_tags, list):
        user_mood_tags = []
    if not audio_url:
        raise ValueError("USER_MUSIC_ANALYZE 缺少 audioUrl")
    logger.warning(
        "[USER_MUSIC_ANALYZE] 开始审核 title=%s artist=%s audio=%s",
        title[:60],
        artist[:40],
        audio_url[:120],
    )
    text_result, text_usage = audit_music_text(title, artist, lyric_text, user_mood_tags)
    audio_result, audio_usage = audit_music_audio(audio_url)
    usage = aggregate_usage([text_usage, audio_usage])
    text_service_error = bool(text_result.get("serviceError"))
    audio_service_error = bool(audio_result.get("serviceError"))
    if text_service_error or audio_service_error:
        logger.warning(
            "[USER_MUSIC_ANALYZE] 审核服务不可用 text_error=%s audio_error=%s",
            text_service_error,
            audio_service_error,
        )
        return {
            **base,
            "finalStatus": "SERVICE_UNAVAILABLE",
            "reviewResult": {
                "pass": False,
                "kind": "service_error",
                "reason": "内部错误，稍后进行重试",
            },
            "usage": usage,
        }

    mood_tags = _merge_music_tags(text_result.get("moodTags"), audio_result.get("moodTags"))
    text_safe = bool(text_result.get("safe", True))
    audio_safe = bool(audio_result.get("safe", True))
    text_risk = _normalize_music_risk(text_result.get("risk"))
    audio_risk = _normalize_music_risk(audio_result.get("risk"))
    merged_risk = text_risk if _RISK_ORDER[text_risk] >= _RISK_ORDER[audio_risk] else audio_risk
    text_violation = (not text_safe) or text_risk == "high"
    audio_violation = (not audio_safe) or audio_risk == "high"
    reasons = _merge_music_reasons(text_result.get("reasons"), audio_result.get("reasons"))
    if text_violation or audio_violation:
        if text_violation and audio_violation:
            reason = "文本、音频内容违规"
        elif text_violation:
            reason = "文本内容违规"
        else:
            reason = "音频内容违规"
        passed = False
        kind = "violation"
    elif merged_risk == "medium":
        # 提示词里 medium 的语义是「把握不足」，不是「确认违规」。
        # 原来这里直接判 violation 并甩一句「文本内容违规」，等于把不确定当成有罪，
        # 作者还看不到模型到底在犹豫什么。仍然拦下，但说清楚是待确认并带上理由。
        side = "文本" if _RISK_ORDER[text_risk] >= _RISK_ORDER[audio_risk] else "音频"
        detail = "；".join(reasons[:3])
        reason = f"{side}内容需要人工确认" + (f"：{detail}" if detail else "")
        passed = False
        kind = "needs_review"
    else:
        reason = ""
        passed = True
        kind = "passed"
    if not passed and kind == "violation" and reasons:
        reason = f"{reason}：" + "；".join(reasons[:3])
    ai_profile = {
        "genre": str(audio_result.get("genre") or "").strip()[:40],
        "energy": str(audio_result.get("energy") or "").strip()[:16],
        "vocal": bool(audio_result.get("vocal", False)),
    }
    if passed:
        _index_music_rag_quietly(task, title, artist, mood_tags, ai_profile)
    return {
        **base,
        "finalStatus": "READY",
        "moodTags": mood_tags,
        "aiProfile": ai_profile,
        "reviewResult": {
            "pass": passed,
            "kind": kind,
            "risk": merged_risk,
            "reason": reason,
            # 两个模块本来就认真收集了 reasons，之前全程没往外传，
            # 被拒的人只看得到一句固定文案，不知道具体存疑点在哪
            "reasons": reasons,
        },
        "usage": usage,
    }


def _merge_music_reasons(*groups: Any) -> list[str]:
    """合并两侧的 reasons：去空白、去重、限长。"""
    out: list[str] = []
    for group in groups:
        if not isinstance(group, list):
            continue
        for item in group:
            text = str(item or "").strip()[:120]
            if text and text not in out:
                out.append(text)
            if len(out) >= 6:
                return out
    return out


def _index_music_rag_quietly(
    task: dict[str, Any],
    title: str,
    artist: str,
    mood_tags: list[str],
    ai_profile: dict[str, Any],
) -> None:
    music_key = str(task.get("musicKey") or task.get("music_key") or "").strip()
    if not music_key:
        logger.warning("[USER_MUSIC_ANALYZE] 跳过向量化：缺少 musicKey")
        return
    try:
        index_music_track({
            "musicKey": music_key,
            "title": title,
            "artist": artist,
            "genre": str(ai_profile.get("genre") or ""),
            "moodTags": mood_tags,
            "aiProfile": json.dumps(ai_profile, ensure_ascii=False),
        })
    except Exception:
        logger.exception("[USER_MUSIC_ANALYZE] 曲目向量化失败 musicKey=%s", music_key)


def _merge_music_tags(first: Any, second: Any) -> list[str]:
    merged: list[str] = []
    for source in (first, second):
        if not isinstance(source, list):
            continue
        for item in source:
            tag = str(item or "").strip()[:16]
            if tag and tag not in merged:
                merged.append(tag)
            if len(merged) >= 8:
                return merged
    return merged


def _merge_music_reasons(first: Any, second: Any) -> list[str]:
    merged: list[str] = []
    for source in (first, second):
        if not isinstance(source, list):
            continue
        for item in source:
            reason = str(item or "").strip()[:200]
            if reason and reason not in merged:
                merged.append(reason)
            if len(merged) >= 8:
                return merged
    return merged


def _normalize_music_risk(value: Any) -> str:
    text = str(value or "low").strip().lower()
    if text in _RISK_ORDER:
        return text
    return "medium"


# pika 的连接和 channel 都不是线程安全的：worker 线程绝不能直接 basic_ack，
# 必须把确认动作交回持有连接的 IO 线程执行
def _settle_threadsafe(connection, channel, delivery_tag: int, ack: bool, requeue: bool = False) -> None:
    def _do() -> None:
        try:
            if ack:
                channel.basic_ack(delivery_tag=delivery_tag)
            else:
                channel.basic_nack(delivery_tag=delivery_tag, requeue=requeue)
        except Exception:
            # 连接期间断开重建过，旧 delivery_tag 已失效；
            # 消息会被 broker 重投，交给 Redis 去重挡住
            logger.warning("[ai_async_worker] 确认消息失败 delivery_tag=%s", delivery_tag)

    try:
        connection.add_callback_threadsafe(_do)
    except Exception:
        logger.warning("[ai_async_worker] 无法投递确认回调，消息将由 broker 重投")


def _handle_message(connection, channel, delivery_tag: int, body: bytes,
                    trace_id: str | None = None) -> None:
    # 线程池的工作线程是复用的，每条消息进来先绑定一次，避免沿用上一条的 traceId
    set_trace_id(trace_id)
    task_id = ""
    try:
        task = json.loads(body.decode("utf-8"))
        task_id = str(task.get("taskId") or "").strip()
        if not task_id:
            _settle_threadsafe(connection, channel, delivery_tag, ack=True)
            return
        if not _try_lock(task_id):
            _settle_threadsafe(connection, channel, delivery_tag, ack=True)
            return
        started = time.time()
        result = _execute(task)
        routing_key = (
            ai_im_result_routing_key()
            if result.get("resultDomain") == "IM"
            else ai_content_result_routing_key()
        )
        if not publish_json(routing_key, result):
            _release(task_id)
            _settle_threadsafe(connection, channel, delivery_tag, ack=False, requeue=True)
            return
        _mark_done(task_id)
        _settle_threadsafe(connection, channel, delivery_tag, ack=True)
        logger.info(
            "[ai_async_worker] 任务完成 task_id=%s type=%s 耗时=%.1fs",
            task_id, task.get("taskType"), time.time() - started,
        )
    except Exception:
        logger.exception("AI异步消息处理失败 task_id=%s", task_id)
        if task_id:
            _release(task_id)
        _settle_threadsafe(connection, channel, delivery_tag, ack=False, requeue=False)


def _consume_loop() -> None:
    queue = ai_async_task_queue()
    pool_size = consumer_worker_threads()
    # 之前是单线程串行：一个总结任务最坏要几分钟，而帖子总结、评论审核、
    # 弹幕审核、举报审核、音乐分析全挤在这一条线上排队，从外面看就像"卡住了"
    executor = ThreadPoolExecutor(max_workers=pool_size, thread_name_prefix="ai-async-exec")
    while True:
        try:
            connection, channel = open_consumer_channel()
            logger.info("[ai_async_worker] 已连接，订阅队列=%s 并发=%s", queue, pool_size)
            # inactivity_timeout 让循环定期空转，_settle_threadsafe 投进来的
            # 确认回调才有机会在 IO 线程上被执行
            for method, properties, body in channel.consume(queue, auto_ack=False, inactivity_timeout=1):
                if method is None:
                    continue
                # traceId 在这里取、传进线程池，不能在 _handle_message 里取：
                # contextvar 不会自动跨到 executor 的工作线程
                trace_id = trace_id_from_headers(getattr(properties, "headers", None))
                executor.submit(
                    _handle_message, connection, channel, method.delivery_tag, body, trace_id
                )
        except Exception:
            logger.exception("AI异步worker连接断开，5秒后重连")
            time.sleep(5)


def start_in_background() -> threading.Thread:
    thread = threading.Thread(target=_consume_loop, name="ai-async-worker", daemon=True)
    thread.start()
    logger.info("[ai_async_worker] 后台线程已启动")
    return thread
