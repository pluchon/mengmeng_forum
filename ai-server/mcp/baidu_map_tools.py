"""百度地图 MCP 工具（HTTP 实现，与官方 MCP Server 能力对齐）."""

from __future__ import annotations

import logging
from typing import Any

from clients.baidu_map_client import directions, geocode, is_configured, search_places, weather

logger = logging.getLogger(__name__)


class BaiduMapGeocodeTool:
    name = "map_geocode"
    description = "将地址解析为经纬度（纬度,经度），用于路线规划前定位。"

    def invoke(self, arguments: dict[str, Any]) -> str:
        if not is_configured():
            return "百度地图未配置：请设置环境变量 BAIDU_MAP_API_KEY（lbsyun.baidu.com 申请）"
        address = str(arguments.get("address") or arguments.get("query") or "").strip()
        if not address:
            return "错误：缺少 address"
        region = str(arguments.get("region") or arguments.get("city") or "").strip()
        return geocode(address, region=region)


class BaiduMapSearchPlacesTool:
    name = "map_search_places"
    description = "地点检索：关键字 + 可选行政区划 region，返回名称、地址与坐标。"

    def invoke(self, arguments: dict[str, Any]) -> str:
        if not is_configured():
            return "百度地图未配置：请设置环境变量 BAIDU_MAP_API_KEY"
        query = str(arguments.get("query") or "").strip()
        if not query:
            return "错误：缺少 query"
        region = str(arguments.get("region") or "").strip()
        return search_places(query, region=region)


class BaiduMapDirectionsTool:
    name = "map_directions"
    description = (
        "路线规划：origin/destination 为「纬度,经度」或中文地址；"
        "mode 可选 driving/walking/riding/transit。"
    )

    def invoke(self, arguments: dict[str, Any]) -> str:
        if not is_configured():
            return "百度地图未配置：请设置环境变量 BAIDU_MAP_API_KEY"
        origin = str(arguments.get("origin") or "").strip()
        destination = str(arguments.get("destination") or "").strip()
        if not origin or not destination:
            return "错误：需要 origin 与 destination"
        mode = str(arguments.get("mode") or "driving").strip().lower()
        return directions(origin, destination, mode=mode)


class BaiduMapWeatherTool:
    name = "map_weather"
    description = "查询指定地点天气（需坐标或地区名，内部先检索地点）。"

    def invoke(self, arguments: dict[str, Any]) -> str:
        if not is_configured():
            return "百度地图未配置：请设置环境变量 BAIDU_MAP_API_KEY"
        # 轻量实现：用地点检索代替独立天气 API，避免额外 endpoint 差异
        location = str(arguments.get("location") or arguments.get("region") or "").strip()
        if not location:
            return "错误：缺少 location"
        return weather(location)
