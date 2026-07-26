"""AI Gateway 与业务模块之间的稳定数据契约。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class ModuleRequest:
    """一次模块调用的最小上下文，业务状态仍由 Java 负责。"""

    task_type: str
    intent: str
    version: str
    request_id: str
    trace_id: str
    user_context: dict[str, Any]
    payload: dict[str, Any]
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ModuleResult:
    """模块对 Gateway 的统一返回，不暴露内部异常细节。"""

    success: bool
    data: dict[str, Any] = field(default_factory=dict)
    error_code: str | None = None
    error_message: str | None = None
    usage: dict[str, Any] = field(default_factory=dict)

    def to_dict(self, request: ModuleRequest) -> dict[str, Any]:
        result: dict[str, Any] = {
            "requestId": request.request_id,
            "traceId": request.trace_id,
            "taskType": request.task_type,
            "intent": request.intent,
            "success": self.success,
            "data": self.data,
            "usage": self.usage,
        }
        if not self.success:
            result["errorCode"] = self.error_code or "AI_MODULE_FAILED"
            result["errorMessage"] = self.error_message or "AI 模块执行失败"
        return result


@dataclass(frozen=True)
class ModuleEvent:
    """Gateway 对外的标准流式事件。"""

    event_type: str
    data: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {"type": self.event_type, "data": self.data}


class ModuleRequestError(ValueError):
    """Gateway 参数或路由不符合契约。"""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
