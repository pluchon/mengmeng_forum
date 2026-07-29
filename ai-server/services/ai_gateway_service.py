"""统一 AI Gateway 的请求规范化与模块路由。"""

from __future__ import annotations

import asyncio
import logging
import uuid
from collections.abc import Iterator
from typing import Any

from modules.moderation import ContentModerationModule
from modules.mascot import MascotChatModule, MascotContextCompressModule
from modules.creation import CoverHintsModule, ImageGenerationModule, PostPolishModule
from modules.game import GobangMoveModule, JiziMoveModule
from modules.search import SearchModule
from modules.summary import PostSummaryModule
from modules.rag import RagIndexArticleModule, RagIndexUserModule, RagRemoveArticleModule
from modules.recommendation import ArticleFeatureModule, UserProfileModule
from runtime.contracts import ModuleEvent, ModuleRequest, ModuleRequestError
from runtime.module_registry import ModuleRegistry

logger = logging.getLogger(__name__)

_registry = ModuleRegistry()
_registry.register("CONTENT_MODERATION", "ARTICLE_AUDIT", "v1", ContentModerationModule())
_registry.register("CONTENT_MODERATION", "TEXT_AUDIT", "v1", ContentModerationModule())
_registry.register("CONTENT_MODERATION", "IMAGE_AUDIT", "v1", ContentModerationModule())
_registry.register("POST_SUMMARY", "GENERATE", "v1", PostSummaryModule())
_registry.register("POST_CREATION", "POLISH", "v1", PostPolishModule())
_registry.register("POST_CREATION", "COVER_HINTS", "v1", CoverHintsModule())
_registry.register("IMAGE_GENERATION", "GENERATE", "v1", ImageGenerationModule())
_registry.register("GAME", "GOBANG_MOVE", "v1", GobangMoveModule())
_registry.register("GAME", "JIZI_MOVE", "v1", JiziMoveModule())
_registry.register("SEARCH", "QUERY", "v1", SearchModule())
_registry.register("RAG", "INDEX_ARTICLE", "v1", RagIndexArticleModule())
_registry.register("RAG", "INDEX_USER", "v1", RagIndexUserModule())
_registry.register("RAG", "REMOVE_ARTICLE", "v1", RagRemoveArticleModule())
_registry.register("RECOMMENDATION", "ARTICLE_FEATURE", "v1", ArticleFeatureModule())
_registry.register("RECOMMENDATION", "USER_PROFILE", "v1", UserProfileModule())
_registry.register("MASCOT", "CHAT", "v1", MascotChatModule())
_registry.register("MASCOT", "CONTEXT_COMPRESS", "v1", MascotContextCompressModule())


def execute_gateway(raw: dict[str, Any]) -> tuple[dict[str, Any], int]:
    """执行统一入口，向 Flask 路由返回已规范化的响应与状态码。"""
    request: ModuleRequest | None = None
    try:
        request = _parse_request(raw)
        result = asyncio.run(_registry.invoke(request))
        return {"code": 200, "msg": "ok", "data": result.to_dict(request)}, 200
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
            "msg": "AI Gateway 执行失败",
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
        yield ModuleEvent("progress", {"status": "preparing", "traceId": request.trace_id}).to_dict()
        for event in _registry.stream(request):
            yield event.to_dict()
        yield ModuleEvent("done", {"success": True, "traceId": request.trace_id}).to_dict()
    except ModuleRequestError as exc:
        yield ModuleEvent("error", {"errorCode": exc.code, "message": exc.message}).to_dict()
        yield ModuleEvent("done", {"success": False, "traceId": request.trace_id}).to_dict()
    except Exception:
        logger.exception("AI Gateway 流式执行失败 trace_id=%s", request.trace_id)
        yield ModuleEvent("error", {"errorCode": "AI_GATEWAY_FAILED", "message": "AI Gateway 执行失败"}).to_dict()
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
