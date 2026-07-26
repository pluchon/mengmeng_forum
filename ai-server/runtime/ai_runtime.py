"""模型与工具调用的最小统一运行时。"""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from typing import Any, TypeVar

from mcp.registry import get_tool_definition

logger = logging.getLogger(__name__)

T = TypeVar("T")


class AiRuntime:
    """集中记录模型/工具调用的 trace、耗时和受控失败信息。"""

    def call_llm(
        self,
        operation: Callable[[], T],
        *,
        trace_id: str,
        model_name: str,
        retries: int = 0,
        fallback: Callable[[], T] | None = None,
        fallback_model_name: str | None = None,
    ) -> T:
        started = time.perf_counter()
        for attempt in range(retries + 1):
            try:
                result = operation()
                logger.info(
                    "AI 模型调用完成 trace_id=%s model=%s attempt=%d cost_ms=%d",
                    trace_id,
                    model_name,
                    attempt + 1,
                    int((time.perf_counter() - started) * 1000),
                )
                return result
            except Exception:
                if attempt < retries:
                    logger.warning(
                        "AI 模型调用失败，准备重试 trace_id=%s model=%s attempt=%d",
                        trace_id,
                        model_name,
                        attempt + 1,
                    )
                    continue
                if fallback is None:
                    logger.exception("AI 模型调用失败 trace_id=%s model=%s", trace_id, model_name)
                    raise
        logger.warning(
            "AI 模型调用失败，使用兜底模型 trace_id=%s model=%s fallback_model=%s",
            trace_id,
            model_name,
            fallback_model_name or "unknown",
        )
        return fallback()

    def call_tool(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        *,
        trace_id: str,
        allowed_tools: set[str],
    ) -> str:
        if not isinstance(arguments, dict):
            raise ValueError("工具参数必须是对象")
        if tool_name not in allowed_tools:
            raise PermissionError("当前模块无权调用该工具")
        definition = get_tool_definition(tool_name)
        if definition is None:
            raise ValueError("未知工具")
        started = time.perf_counter()
        result = definition.tool.invoke(arguments)
        logger.info(
            "AI 工具调用完成 trace_id=%s tool=%s risk_level=%s cost_ms=%d",
            trace_id,
            definition.name,
            definition.risk_level,
            int((time.perf_counter() - started) * 1000),
        )
        return result
