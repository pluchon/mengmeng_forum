"""MCP 工具：tavily_search — 调用 Tavily Search API."""

from __future__ import annotations

import logging
from typing import Any

from clients.tavily_client import TavilySearchClient
from config import settings

logger = logging.getLogger(__name__)


class TavilySearchMcpTool:
    name = "tavily_search"
    description = (
        "联网搜索实时信息。输入 query（搜索词），返回摘要与来源链接。"
        "适用于本地知识库未覆盖的事实、人物、事件、产品外观等。"
    )

    def invoke(self, arguments: dict[str, Any]) -> str:
        query = str(arguments.get("query") or "").strip()
        if not query:
            return "错误：缺少 query 参数"
        cfg = settings.tavily
        if not cfg.get("enabled", True):
            return "联网搜索未启用"
        client = TavilySearchClient()
        if not client.is_configured():
            return "Tavily API Key 未配置（请设置环境变量 TAVILY_API_KEY）"
        try:
            return client.search_for_context(
                query,
                max_results=int(arguments.get("max_results") or cfg.get("max_results", 5)),
                search_depth=str(arguments.get("search_depth") or cfg.get("search_depth", "basic")),
            )
        except Exception as e:
            logger.exception("tavily_search MCP 调用失败")
            return f"搜索失败: {e}"
