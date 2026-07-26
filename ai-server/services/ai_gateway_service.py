"""统一 AI Gateway 的请求规范化与模块路由。"""

from __future__ import annotations

import asyncio
import logging
import uuid
from typing import Any

from modules.moderation import ContentModerationModule
from runtime.contracts import ModuleRequest, ModuleRequestError
from runtime.module_registry import ModuleRegistry

logger = logging.getLogger(__name__)

_registry = ModuleRegistry()
_registry.register("CONTENT_MODERATION", "ARTICLE_AUDIT", "v1", ContentModerationModule())


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
