"""Java 论坛后端 HTTP 客户端（公告中心等只读接口）."""

from __future__ import annotations

import logging
from typing import Any

import requests

from config import settings

logger = logging.getLogger(__name__)

_SUCCESS_CODE = 0


def _base_url() -> str:
    return (settings.forum.get("base_url") or "http://localhost:10086").rstrip("/")


def _timeout() -> int:
    return int(settings.forum.get("request_timeout", 15))


def list_published_notices() -> list[dict[str, Any]]:
    """拉取用户端公告中心已发布列表；失败返回空列表."""
    path = str(settings.forum.get("notice_list_path") or "/notice/center/list")
    url = f"{_base_url()}{path}"
    try:
        response = requests.get(url, timeout=_timeout())
        response.raise_for_status()
        payload = response.json()
        if not isinstance(payload, dict):
            logger.warning("[forum_backend] 公告列表响应非对象 url=%s", url)
            return []
        if payload.get("code") != _SUCCESS_CODE:
            logger.warning(
                "[forum_backend] 公告列表业务失败 code=%s msg=%s",
                payload.get("code"),
                str(payload.get("message", ""))[:200],
            )
            return []
        data = payload.get("data")
        if not isinstance(data, list):
            return []
        items = [item for item in data if isinstance(item, dict)]
        logger.info("[forum_backend] 已拉取公告 %s 条", len(items))
        return items
    except Exception:
        logger.exception("[forum_backend] 拉取公告列表失败 url=%s", url)
        return []
