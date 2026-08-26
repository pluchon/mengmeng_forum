"""帖子总结与文本审核通用异步worker。"""

from __future__ import annotations

import asyncio
import json
import logging
import threading
import time
from typing import Any

import redis

from clients.rabbit import (
    ai_async_task_queue,
    ai_content_result_routing_key,
    ai_im_result_routing_key,
    open_consumer_channel,
    publish_json,
)
from clients.redis_client import redis_client
from modules.moderation import ContentModerationModule
from modules.summary.graph import run_summary_graph
from modules.creation.music_audit_audio import audit_music_audio
from modules.creation.music_audit_text import audit_music_text
from modules.creation.usage import aggregate_usage
from rag.music_indexer import index_music_track
from runtime.contracts import ModuleRequest

logger = logging.getLogger(__name__)
_TASK_PREFIX = "ai_async:task_state:"
_TASK_TTL = 3600


def _task_key(task_id: str) -> str:
    return f"{_TASK_PREFIX}{task_id}"


def _try_lock(task_id: str) -> bool:
    try:
        return bool(redis_client.set(_task_key(task_id), "running", ex=_TASK_TTL, nx=True))
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
                payload={"title": task.get("title") or "", "content": task.get("content") or ""},
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
        reason = "文本内容违规" if _RISK_ORDER[text_risk] >= _RISK_ORDER[audio_risk] else "音频内容违规"
        passed = False
        kind = "violation"
    else:
        reason = ""
        passed = True
        kind = "passed"
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
        },
        "usage": usage,
    }


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


def _consume_loop() -> None:
    queue = ai_async_task_queue()
    while True:
        try:
            connection, channel = open_consumer_channel()
            logger.info("[ai_async_worker] 已连接，订阅队列=%s", queue)
            for method, _properties, body in channel.consume(queue, auto_ack=False, inactivity_timeout=None):
                if method is None:
                    continue
                task_id = ""
                try:
                    task = json.loads(body.decode("utf-8"))
                    task_id = str(task.get("taskId") or "").strip()
                    if not task_id:
                        channel.basic_ack(delivery_tag=method.delivery_tag)
                        continue
                    if not _try_lock(task_id):
                        channel.basic_ack(delivery_tag=method.delivery_tag)
                        continue
                    result = _execute(task)
                    routing_key = (
                        ai_im_result_routing_key()
                        if result.get("resultDomain") == "IM"
                        else ai_content_result_routing_key()
                    )
                    if not publish_json(routing_key, result):
                        _release(task_id)
                        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
                        continue
                    _mark_done(task_id)
                    channel.basic_ack(delivery_tag=method.delivery_tag)
                except Exception:
                    logger.exception("AI异步消息处理失败")
                    if task_id:
                        _release(task_id)
                    channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        except Exception:
            logger.exception("AI异步worker连接断开，5秒后重连")
            time.sleep(5)


def start_in_background() -> threading.Thread:
    thread = threading.Thread(target=_consume_loop, name="ai-async-worker", daemon=True)
    thread.start()
    logger.info("[ai_async_worker] 后台线程已启动")
    return thread
