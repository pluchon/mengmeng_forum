"""当前时间上下文工具."""

from __future__ import annotations

from typing import Any

from utils.datetime_context import build_datetime_context


class CurrentDatetimeTool:
    name = "get_current_datetime"
    description = "获取用户设备或服务器当前时间（年月日、星期、时刻），用于出行日期与行程说明。"

    def invoke(self, arguments: dict[str, Any]) -> str:
        client_iso = str(arguments.get("client_datetime") or arguments.get("clientDatetime") or "").strip()
        return build_datetime_context(client_iso or None)
