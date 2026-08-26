"""LangGraph 扇出图的统一 invoke 配置（轻量并发上限）。"""

from __future__ import annotations

from typing import Any

# 官方建议用 max_concurrency 保护模型限流与小机资源；与 gateway long 档同量级
FANOUT_INVOKE_CONFIG: dict[str, Any] = {"max_concurrency": 3}


def invoke_with_fanout_limit(graph: Any, state: dict[str, Any]) -> Any:
    return graph.invoke(state, config=FANOUT_INVOKE_CONFIG)
