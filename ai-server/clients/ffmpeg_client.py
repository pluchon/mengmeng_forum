"""FFmpeg 转码服务 HTTP 客户端."""

from __future__ import annotations

import base64
import logging

import requests

from config import settings

logger = logging.getLogger(__name__)


def _base_url() -> str:
    return (settings.ffmpeg.get("base_url") or "http://ffmpeg:8099").rstrip("/")


def _internal_headers() -> dict[str, str]:
    key = (settings.ffmpeg.get("internal_key") or "").strip()
    if not key:
        return {}
    return {"X-Internal-Key": key}


def extract_audit_frames(
    url: str,
    *,
    count: int = 4,
    timeout: int | None = None,
) -> list[bytes]:
    """从视频 URL 抽取 JPEG 帧；失败或空结果时返回空列表."""
    endpoint = f"{_base_url()}/extract-audit-frames"
    if timeout is None:
        timeout = int(settings.audit.get("video_frame_extract_timeout", 600))
    try:
        response = requests.post(
            endpoint,
            json={"url": url, "count": count},
            headers=_internal_headers(),
            timeout=timeout,
        )
        response.raise_for_status()
        data = response.json() or {}
        frames: list[bytes] = []
        for item in data.get("frames") or []:
            if not item:
                continue
            try:
                frames.append(base64.b64decode(item))
            except Exception:
                continue
        return frames
    except Exception:
        logger.exception("[ffmpeg_client] 抽帧失败 url=%s", url[:120])
        return []
