"""进程内模块注册表；未来可替换为 HTTP 或 MQ 适配器。"""

from __future__ import annotations

from collections.abc import Iterator
from typing import Protocol

from runtime.contracts import ModuleEvent, ModuleRequest, ModuleRequestError, ModuleResult


class AiModule(Protocol):
    """所有 AI 模块只向注册表暴露的入口。"""

    async def run(self, request: ModuleRequest) -> ModuleResult: ...


class StreamingAiModule(AiModule, Protocol):
    """支持逐事件输出的模块契约。"""

    def stream(self, request: ModuleRequest) -> Iterator[ModuleEvent]: ...


class ModuleRegistry:
    """以 taskType / intent / version 精确路由模块。"""

    def __init__(self) -> None:
        self._modules: dict[tuple[str, str, str], AiModule] = {}

    def register(self, task_type: str, intent: str, version: str, module: AiModule) -> None:
        key = (task_type.strip().upper(), intent.strip().upper(), version.strip().lower())
        if not all(key):
            raise ValueError("模块注册 taskType、intent、version 不能为空")
        if key in self._modules:
            raise ValueError(f"AI 模块重复注册: {key}")
        self._modules[key] = module

    async def invoke(self, request: ModuleRequest) -> ModuleResult:
        key = (request.task_type.upper(), request.intent.upper(), request.version.lower())
        module = self._modules.get(key)
        if module is None:
            raise ModuleRequestError("AI_MODULE_NOT_FOUND", "不支持的 AI 任务类型、意图或版本")
        return await module.run(request)

    def stream(self, request: ModuleRequest) -> Iterator[ModuleEvent]:
        """优先使用模块的流式实现，其他模块保持最终结果事件兼容。"""
        key = (request.task_type.upper(), request.intent.upper(), request.version.lower())
        module = self._modules.get(key)
        if module is None:
            raise ModuleRequestError("AI_MODULE_NOT_FOUND", "不支持的 AI 任务类型、意图或版本")
        stream_method = getattr(module, "stream", None)
        if callable(stream_method):
            yield from stream_method(request)
            return
        result = asyncio.run(module.run(request))
        yield ModuleEvent("final", result.to_dict(request))

    def registered_routes(self) -> list[tuple[str, str, str]]:
        return sorted(self._modules.keys())
