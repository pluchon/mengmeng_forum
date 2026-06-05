"""百度地图 Web 服务 API（与 @baidumap/mcp-server-baidu-map 同源能力，便于 Docker 内无需 Node）."""

from __future__ import annotations

import logging
import re
from typing import Any
from urllib.parse import urlencode

import requests

from config import settings

logger = logging.getLogger(__name__)

_BASE = "https://api.map.baidu.com"


def _ak() -> str:
    cfg = settings.baidu_map
    return str(cfg.get("api_key") or "").strip()


def is_configured() -> bool:
    return bool(_ak())


def _get(path: str, params: dict[str, Any], *, timeout: int = 20) -> dict[str, Any]:
    key = _ak()
    if not key:
        return {"status": -1, "message": "BAIDU_MAP_API_KEY 未配置"}
    q = dict(params)
    q["ak"] = key
    q["output"] = "json"
    url = f"{_BASE}{path}?{urlencode(q)}"
    try:
        r = requests.get(url, timeout=timeout)
        r.raise_for_status()
        data = r.json()
        if isinstance(data, dict):
            return data
        return {"status": -2, "message": "invalid response"}
    except Exception as e:
        logger.exception("百度地图 API 请求失败 path=%s", path)
        return {"status": -3, "message": str(e)}


def geocode(address: str, *, region: str = "") -> str:
    """地址 -> 经纬度（纬度,经度）."""
    params: dict[str, Any] = {"address": address}
    if region:
        params["city"] = region
    data = _get("/geocoding/v3/", params)
    if data.get("status") != 0:
        return f"地理编码失败: {data.get('message', data)}"
    result = data.get("result") or {}
    loc = result.get("location") or {}
    lat, lng = loc.get("lat"), loc.get("lng")
    if lat is None or lng is None:
        return "未解析到坐标"
    return f"{lat},{lng}（{address}）"


def search_places(query: str, *, region: str = "") -> str:
    params: dict[str, Any] = {
        "query": query,
        "page_size": 5,
        "page_num": 0,
    }
    if region:
        params["region"] = region
    data = _get("/place/v2/search", params)
    if data.get("status") != 0:
        return f"地点检索失败: {data.get('message', data)}"
    results = data.get("results") or []
    if not results:
        return f"未找到与「{query}」相关的地点"
    lines = []
    for i, p in enumerate(results[:5], 1):
        name = p.get("name", "")
        addr = p.get("address", "")
        loc = p.get("location") or {}
        lat, lng = loc.get("lat"), loc.get("lng")
        coord = f"{lat},{lng}" if lat is not None and lng is not None else ""
        lines.append(f"{i}. {name} | {addr} | {coord}")
    return "\n".join(lines)


def directions(
    origin: str,
    destination: str,
    *,
    mode: str = "driving",
) -> str:
    """
    origin/destination: 纬度,经度 或 地址（地址会先 geocode）.
    mode: driving | walking | riding | transit
    """
    mode_map = {
        "driving": ("directionlite/v1/driving", "driving"),
        "walking": ("directionlite/v1/walking", "walking"),
        "riding": ("directionlite/v1/riding", "riding"),
        "transit": ("directionlite/v1/transit", "transit"),
    }
    path_tpl = mode_map.get(mode, mode_map["driving"])

    _COORD_RE = re.compile(r"^-?\d+(\.\d+)?,-?\d+(\.\d+)?$")

    def _to_coord(raw: str) -> str | None:
        raw = raw.strip()
        if _COORD_RE.match(raw):
            return raw
        geo = geocode(raw)
        head = geo.split("（")[0].strip()
        if _COORD_RE.match(head):
            return head
        return None

    o = _to_coord(origin)
    d = _to_coord(destination)
    if not o or not d:
        return "起点或终点无法解析为坐标，请提供更具体的地名"

    path, _ = path_tpl
    data = _get(f"/{path}", {"origin": o, "destination": d})
    if data.get("status") != 0:
        return f"路线规划失败: {data.get('message', data)}"
    result = data.get("result") or {}
    routes = result.get("routes") or []
    if not routes:
        return "未找到可用路线"
    r0 = routes[0]
    dist = r0.get("distance")
    dur = r0.get("duration")
    dist_km = f"{int(dist) / 1000:.1f}公里" if dist else ""
    dur_min = f"{int(dur) / 60:.0f}分钟" if dur else ""
    summary = f"方式={mode}，约 {dist_km}，预计 {dur_min}。"
    steps = r0.get("steps") or []
    step_lines = []
    table_rows = []
    for i, s in enumerate(steps[:12], 1):
        instr = (s.get("instruction") or s.get("stepInstruction") or "").strip()
        road = (s.get("road_name") or s.get("road") or "").strip()
        leg_dist = s.get("distance")
        leg_dur = s.get("duration")
        leg_km = f"{int(leg_dist) / 1000:.1f}km" if leg_dist else ""
        leg_min = f"{int(leg_dur) / 60:.0f}分" if leg_dur else ""
        if instr:
            step_lines.append(f"{i}. {instr}")
        table_rows.append(f"| {i} | {road or instr[:24]} | {leg_km} | {leg_min} | {instr[:80]} |")
    parts = [summary]
    if table_rows:
        parts.append(
            "| 阶段 | 路段/说明 | 本段距离 | 本段用时 | 导航指引 |\n"
            "| --- | --- | --- | --- | --- |\n" + "\n".join(table_rows)
        )
    elif step_lines:
        parts.append("\n".join(step_lines))
    return "\n".join(parts)


def weather(location: str) -> str:
    """地点名 -> 地理编码 -> 国内天气（weather/v1）."""
    loc = (location or "").strip()
    if not loc:
        return "错误：缺少 location"
    geo_data = _get("/geocoding/v3/", {"address": loc})
    if geo_data.get("status") != 0:
        return f"天气：无法解析地点「{loc}」({geo_data.get('message', '')})"
    result = geo_data.get("result") or {}
    comp = result.get("addressComponent") or {}
    district_id = comp.get("adcode") or comp.get("city_code") or ""
    loc_pt = result.get("location") or {}
    lat, lng = loc_pt.get("lat"), loc_pt.get("lng")

    params: dict[str, Any] = {"data_type": "now"}
    if district_id:
        params["district_id"] = str(district_id)
    elif lat is not None and lng is not None:
        params["location"] = f"{lng},{lat}"
        params["coordtype"] = "bd09ll"
    else:
        return f"天气：无法获取「{loc}」的行政区划或坐标"

    data = _get("/weather/v1/", params)
    if data.get("status") != 0:
        return f"天气查询失败: {data.get('message', data)}（若未开通天气服务，请在百度地图控制台开通）"
    now = data.get("result", {}).get("now") or {}
    if not now:
        return f"「{loc}」暂无实况天气数据"
    temp = now.get("temp")
    text = now.get("text") or now.get("weather") or ""
    wind = now.get("wind_dir") or ""
    wind_class = now.get("wind_class") or ""
    rh = now.get("rh")
    feels = now.get("feels_like")
    lines = [f"地点：{loc}", f"天气：{text}", f"气温：{temp}℃" if temp is not None else ""]
    if feels is not None:
        lines.append(f"体感：{feels}℃")
    if rh is not None:
        lines.append(f"湿度：{rh}%")
    if wind or wind_class:
        lines.append(f"风：{wind} {wind_class}".strip())
    return "\n".join(x for x in lines if x)
