from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from mcp.base import McpTool
from mcp.datetime_tool import CurrentDatetimeTool
from mcp.tavily_search import TavilySearchMcpTool

_TOOLS: dict[str, McpTool] = {
    TavilySearchMcpTool.name: TavilySearchMcpTool(),
    CurrentDatetimeTool.name: CurrentDatetimeTool(),
}


@dataclass(frozen=True)
class ToolDefinition:
    """工具的最小注册元数据，供 AiRuntime 做白名单和审计。"""

    name: str
    description: str
    risk_level: str
    input_schema: dict[str, Any]
    output_schema: dict[str, Any]
    tool: McpTool


_TOOL_DEFINITIONS: dict[str, ToolDefinition] = {
    name: ToolDefinition(
        name=name,
        description=tool.description,
        risk_level="L1",
        input_schema={"type": "object"},
        output_schema={"type": "string"},
        tool=tool,
    )
    for name, tool in _TOOLS.items()
}


def list_tools() -> list[str]:
    return list(_TOOLS.keys())


def get_tool(name: str) -> McpTool | None:
    return _TOOLS.get(name)


def get_tool_definition(name: str) -> ToolDefinition | None:
    return _TOOL_DEFINITIONS.get(name)


def invoke_tool(name: str, arguments: dict[str, Any]) -> str:
    definition = get_tool_definition(name)
    if definition is None:
        raise ValueError(f"未知 MCP 工具: {name}")
    return definition.tool.invoke(arguments)
