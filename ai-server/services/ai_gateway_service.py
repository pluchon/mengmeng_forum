"""统一 AI Gateway 的请求规范化与模块路由。"""

from __future__ import annotations

import asyncio
import logging
import threading
import uuid
from collections.abc import Iterator
from typing import Any

from config import settings

from modules.moderation import ContentModerationModule
from modules.mascot import MascotChatModule, MascotContextCompressModule, MascotMemoryEditModule
from modules.creation import (
    ArticleCoverModule,
    ArticleTagRecommendModule,
    ArticleTagSimilarityModule,
    CoverHintsModule,
    ImageGenerationModule,
    MusicRecommendModule,
    MusicSearchModule,
    PostPolishModule,
)
from modules.creator_insight import CreatorInsightModule
from modules.game import GobangMoveModule
from modules.search import SearchModule
from modules.summary import PostSummaryModule
from modules.rag import (
    RagIndexArticleModule,
    RagIndexEmojiModule,
    RagIndexMusicModule,
    RagIndexUserModule,
    RagRemoveArticleModule,
)
from modules.recommendation import ArticleFeatureModule, MusicTasteModule, UserProfileModule
from runtime.contracts import ModuleEvent, ModuleRequest, ModuleRequestError
from runtime.module_registry import ModuleRegistry

logger = logging.getLogger(__name__)

_gateway_cfg = settings.gateway
_semaphores = {
    "fast": threading.BoundedSemaphore(int(_gateway_cfg.get("fast_concurrency", 8))),
    "standard": threading.BoundedSemaphore(int(_gateway_cfg.get("standard_concurrency", 4))),
    "long": threading.BoundedSemaphore(int(_gateway_cfg.get("long_concurrency", 2))),
    "index": threading.BoundedSemaphore(int(_gateway_cfg.get("index_concurrency", 2))),
}

_registry = ModuleRegistry()
_registry.register("CONTENT_MODERATION", "ARTICLE_AUDIT", "v1", ContentModerationModule())
_registry.register("CONTENT_MODERATION", "TEXT_AUDIT", "v1", ContentModerationModule())
_registry.register("CONTENT_MODERATION", "IMAGE_AUDIT", "v1", ContentModerationModule())
_registry.register("POST_SUMMARY", "GENERATE", "v1", PostSummaryModule())
_registry.register("CREATOR_INSIGHT", "GENERATE", "v1", CreatorInsightModule())
_registry.register("POST_CREATION", "POLISH", "v1", PostPolishModule())
_registry.register("POST_CREATION", "COVER_HINTS", "v1", CoverHintsModule())
_registry.register("POST_CREATION", "COVER_GENERATE", "v1", ArticleCoverModule())
_registry.register("POST_CREATION", "TAG_RECOMMEND", "v1", ArticleTagRecommendModule())
_registry.register("POST_CREATION", "TAG_SIMILARITY", "v1", ArticleTagSimilarityModule())
_registry.register("POST_CREATION", "MUSIC_RECOMMEND", "v1", MusicRecommendModule())
_registry.register("POST_CREATION", "MUSIC_SEARCH", "v1", MusicSearchModule())
_registry.register("IMAGE_GENERATION", "GENERATE", "v1", ImageGenerationModule())
_registry.register("GAME", "GOBANG_MOVE", "v1", GobangMoveModule())
_registry.register("SEARCH", "QUERY", "v1", SearchModule())
_registry.register("RAG", "INDEX_ARTICLE", "v1", RagIndexArticleModule())
_registry.register("RAG", "INDEX_EMOJI", "v1", RagIndexEmojiModule())
_registry.register("RAG", "INDEX_MUSIC", "v1", RagIndexMusicModule())
_registry.register("RAG", "INDEX_USER", "v1", RagIndexUserModule())
_registry.register("RAG", "REMOVE_ARTICLE", "v1", RagRemoveArticleModule())
_registry.register("RECOMMENDATION", "ARTICLE_FEATURE", "v1", ArticleFeatureModule())
_registry.register("RECOMMENDATION", "USER_PROFILE", "v1", UserProfileModule())
_registry.register("RECOMMENDATION", "MUSIC_TASTE", "v1", MusicTasteModule())
_registry.register("MASCOT", "CHAT", "v1", MascotChatModule())
_registry.register("MASCOT", "CONTEXT_COMPRESS", "v1", MascotContextCompressModule())
_registry.register("MASCOT", "MEMORY_EDIT", "v1", MascotMemoryEditModule())


def execute_gateway(raw: dict[str, Any]) -> tuple[dict[str, Any], int]:
    """执行统一入口，向 Flask 路由返回已规范化的响应与状态码。"""
    request: ModuleRequest | None = None
    try:
        request = _parse_request(raw)
        semaphore = _semaphore_for(request)
        if not semaphore.acquire(blocking=False):
            return _busy_response(request)
        try:
            result = asyncio.run(_registry.invoke(request))
            return {"code": 200, "msg": "ok", "data": result.to_dict(request)}, 200
        finally:
            semaphore.release()
    except ModuleRequestError as exc:
        return {
            "code": 400,
            "msg": exc.message,
            "data": {
                "requestId": request.request_id if request else "",
                "traceId": request.trace_id if request else "",
                "errorCode": exc.code,
            },
        }, 400
    except Exception:
        trace_id = request.trace_id if request else ""
        logger.exception("AI Gateway 执行失败 trace_id=%s", trace_id)
        return {
            "code": 500,
            "msg": "AI 服务暂时不可用，请稍后再试",
            "data": {
                "requestId": request.request_id if request else "",
                "traceId": trace_id,
                "errorCode": "AI_GATEWAY_FAILED",
            },
        }, 500


def registered_routes() -> list[tuple[str, str, str]]:
    """仅供健康检查和单元测试读取，不暴露为公共业务接口。"""
    return _registry.registered_routes()


def stream_gateway(raw: dict[str, Any]) -> Iterator[dict[str, Any]]:
    """把标准模块执行包装为 SSE 可消费的事件序列。"""
    try:
        request = _parse_request(raw)
    except ModuleRequestError as exc:
        yield ModuleEvent("error", {"errorCode": exc.code, "message": exc.message}).to_dict()
        yield ModuleEvent("done", {"success": False}).to_dict()
        return

    try:
        semaphore = _semaphore_for(request)
        if not semaphore.acquire(blocking=False):
            yield ModuleEvent(
                "error",
                {"errorCode": "AI_GATEWAY_BUSY", "message": "AI 服务正忙，请稍后再试"},
            ).to_dict()
            yield ModuleEvent("done", {"success": False, "traceId": request.trace_id}).to_dict()
            return
        try:
            yield ModuleEvent("progress", {"status": "preparing", "traceId": request.trace_id}).to_dict()
            for event in _registry.stream(request):
                yield event.to_dict()
            yield ModuleEvent("done", {"success": True, "traceId": request.trace_id}).to_dict()
        finally:
            semaphore.release()
    except ModuleRequestError as exc:
        yield ModuleEvent("error", {"errorCode": exc.code, "message": exc.message}).to_dict()
        yield ModuleEvent("done", {"success": False, "traceId": request.trace_id}).to_dict()
    except Exception:
        logger.exception("AI Gateway 流式执行失败 trace_id=%s", request.trace_id)
        yield ModuleEvent("error", {"errorCode": "AI_GATEWAY_FAILED",
                                    "message": "AI 服务暂时不可用，请稍后再试"}).to_dict()
        yield ModuleEvent("done", {"success": False, "traceId": request.trace_id}).to_dict()


def _parse_request(raw: dict[str, Any]) -> ModuleRequest:
    task_type = _required_text(raw, "taskType").upper()
    intent = _required_text(raw, "intent").upper()
    version = str(raw.get("version") or "v1").strip().lower()
    if not version:
        raise ModuleRequestError("INVALID_GATEWAY_REQUEST", "version 不能为空")
    trace_id = str(raw.get("traceId") or uuid.uuid4().hex).strip()
    request_id = str(raw.get("requestId") or trace_id).strip()
    if len(trace_id) > 128 or len(request_id) > 128:
        raise ModuleRequestError("INVALID_GATEWAY_REQUEST", "traceId 或 requestId 过长")
    return ModuleRequest(
        task_type=task_type,
        intent=intent,
        version=version,
        request_id=request_id,
        trace_id=trace_id,
        user_context=_object_value(raw, "userContext"),
        payload=_object_value(raw, "payload"),
        metadata=_object_value(raw, "metadata"),
    )


def _semaphore_for(request: ModuleRequest) -> threading.BoundedSemaphore:
    if request.task_type == "IMAGE_GENERATION" or request.intent == "COVER_GENERATE":
        return _semaphores["long"]
    if request.task_type == "RAG" and request.intent.startswith(("INDEX_", "REMOVE_")):
        return _semaphores["index"]
    if request.task_type in {"CONTENT_MODERATION", "GAME"} or request.intent == "TAG_SIMILARITY":
        return _semaphores["fast"]
    return _semaphores["standard"]


def _busy_response(request: ModuleRequest) -> tuple[dict[str, Any], int]:
    return {
        "code": 429,
        "msg": "AI 服务繁忙，请稍后重试",
        "data": {
            "requestId": request.request_id,
            "traceId": request.trace_id,
            "errorCode": "AI_GATEWAY_BUSY",
        },
    }, 429


def _required_text(raw: dict[str, Any], field: str) -> str:
    value = str(raw.get(field) or "").strip()
    if not value:
        raise ModuleRequestError("INVALID_GATEWAY_REQUEST", f"{field} 不能为空")
    return value


def _object_value(raw: dict[str, Any], field: str) -> dict[str, Any]:
    value = raw.get(field) or {}
    if not isinstance(value, dict):
        raise ModuleRequestError("INVALID_GATEWAY_REQUEST", f"{field} 必须是对象")
    return value
