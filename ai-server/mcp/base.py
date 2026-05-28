from __future__ import annotations

from typing import Any, Protocol


class McpTool(Protocol):
    name: str
    description: str

    def invoke(self, arguments: dict[str, Any]) -> str: ...
