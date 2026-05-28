"""生图前通过 MCP(Tavily) 补充视觉参考."""

from __future__ import annotations

import logging

from mcp.registry import invoke_tool
from utils.mcp_routing import assess_mcp_for_image

logger = logging.getLogger(__name__)


def enrich_image_prompt(prompt: str) -> tuple[str, bool]:
    """
    若需要联网则检索并拼接到 prompt。
    返回 (enhanced_prompt, mcp_used).
    """
    need, query = assess_mcp_for_image(prompt)
    if not need or not query:
        return prompt, False
    try:
        ctx = invoke_tool("tavily_search", {"query": query})
    except Exception:
        logger.exception("生图 MCP 检索失败")
        return prompt, False
    if not ctx or ctx.startswith("错误") or ctx.startswith("搜索失败"):
        return prompt, False
    enhanced = (
        f"{prompt.strip()}\n\n"
        f"[参考资料（请据此把握主体外观与关键细节，用中文生图提示词表达）]\n{ctx[:2000]}"
    )
    return enhanced[:4000], True
