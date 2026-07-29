"""
看板娘 MCP 编排：联网搜索、城市级天气与当前时间.
"""

from __future__ import annotations

import logging
import re
from typing import Any

from config import settings
from mcp.registry import invoke_tool
from utils.datetime_context import build_datetime_context
from utils.mcp_routing import assess_mcp_for_writing

logger = logging.getLogger(__name__)

_LOCAL_LIFE_RE = re.compile(
    r"天气|温度|下雨|降雨|带伞|穿什么|冷不冷|热不热|空气|"
    r"出门|外出|散步|跑步|骑行|徒步|露营|野餐|逛街|遛狗|下班|周末",
    re.I,
)


def _baidu_enabled() -> bool:
    mcp = settings.raw.get("mcp", {}) or {}
    if not mcp.get("enabled", True):
        return False
    bm = settings.baidu_map
    if not bm.get("enabled", True):
        return False
    from clients.baidu_map_client import is_configured

    return is_configured()


def prepare_mascot_mcp_bundle(
    *,
    message: str,
    history: list[dict[str, str]] | None,
    skill: str,
    client_datetime: str | None,
    client_location: str | None = None,
) -> dict[str, Any]:
    """
    返回 datetime_context, local_kb_snippet, mcp_context、联网搜索及图集请求信息。
    """
    datetime_ctx = build_datetime_context(client_datetime)
    out: dict[str, Any] = {
        "datetime_context": datetime_ctx,
        "local_kb_snippet": "",
        "mcp_context": "",
        "need_mcp_search": False,
        "need_search_images": False,
        "mcp_query": "",
        "mcp_used": False,
    }
    ctx_parts: list[str] = [datetime_ctx]

    if skill == "writing":
        need, query, snippet, need_search_images = assess_mcp_for_writing(message, history)
        out["need_search_images"] = need_search_images
        out["local_kb_snippet"] = snippet
        out["need_mcp_search"] = need
        out["mcp_query"] = query

    location = str(client_location or "").strip()[:80]
    if skill == "writing" and location and _LOCAL_LIFE_RE.search(message) and _baidu_enabled():
        try:
            weather_context = invoke_tool("map_weather", {"location": location})
            if weather_context:
                ctx_parts.append(f"【用户所在城市的生活参考】\n{weather_context}")
                out["mcp_used"] = True
        except Exception:
            logger.exception("用户所在城市天气查询失败")

    out["mcp_context"] = "\n\n".join(p for p in ctx_parts if p)
    return out
