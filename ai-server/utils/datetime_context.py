"""用户/服务器当前时间上下文，供看板娘回复与出行规划."""

from __future__ import annotations

from datetime import datetime, timezone
from zoneinfo import ZoneInfo

_TAIPEI = ZoneInfo("Asia/Taipei")


def _parse_client_iso(raw: str | None) -> datetime | None:
    if not raw or not str(raw).strip():
        return None
    text = str(raw).strip().replace("Z", "+00:00")
    try:
        dt = datetime.fromisoformat(text)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except ValueError:
        return None


def build_datetime_context(client_datetime: str | None = None) -> str:
    """
    优先使用前端传来的用户本地时间（ISO8601），否则用服务器 Asia/Taipei.
    """
    client_dt = _parse_client_iso(client_datetime)
    server_dt = datetime.now(_TAIPEI)
    if client_dt is not None:
        try:
            local = client_dt.astimezone(_TAIPEI)
        except Exception:
            local = server_dt
        offset = client_dt.utcoffset()
        tz_note = "用户设备时区"
    else:
        local = server_dt
        tz_note = "服务器时区（未收到用户设备时间）"
    weekdays = ("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    wd = weekdays[local.weekday()]
    return (
        f"当前参考时间（{tz_note}）：{local.strftime('%Y年%m月%d日')} {wd} "
        f"{local.strftime('%H:%M')}（Asia/Taipei 展示）。"
        f"服务器时间：{server_dt.strftime('%Y-%m-%d %H:%M')}。"
    )
