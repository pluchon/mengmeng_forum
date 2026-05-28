from __future__ import annotations

from typing import Any

from mcp.base import McpTool
from mcp.tavily_search import TavilySearchMcpTool

_TOOLS: dict[str, McpTool] = {
    TavilySearchMcpTool.name: TavilySearchMcpTool(),
}


def list_tools() -> list[str]:
    return list(_TOOLS.keys())


def get_tool(name: str) -> McpTool | None:
    return _TOOLS.get(name)


def invoke_tool(name: str, arguments: dict[str, Any]) -> str:
    tool = get_tool(name)
    if tool is None:
        raise ValueError(f"未知 MCP 工具: {name}")
    return tool.invoke(arguments)
