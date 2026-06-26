"""视频帖审核：优先 DashScope 拉 URL；失败或超大文件则抽帧走图片审核."""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

import requests
from langchain_core.messages import HumanMessage

from clients.dashscope_chat_client import dashscope_chat_completion, lc_messages_to_openai
from clients.ffmpeg_client import extract_audit_frames
from config import settings
from graphs.prompts import VIDEO_AUDIT_PROMPT
from utils.image import validate_image_bytes
from utils.oss_media import head_object_size, presign_get_url, probe_url_readable

logger = logging.getLogger(__name__)


def _video_max_bytes() -> int:
    return int(settings.audit.get("video_max_bytes_for_url", 100 * 1024 * 1024))


def _video_timeout() -> int:
    return int(settings.audit.get("video_audit_timeout", 300))


def _resolve_video_url(url: str) -> str:
    signed = presign_get_url(url)
    if probe_url_readable(signed):
        return signed
    if signed != url and probe_url_readable(url):
        return url
    return signed


def _dashscope_video_audit(resolved_url: str) -> tuple[bool, str]:
    plus_model = settings.dashscope.get("model_vision_fallback", "qwen3-vl-plus")
    openai_msgs = lc_messages_to_openai([HumanMessage(content=[
        {"video": resolved_url},
        {"text": VIDEO_AUDIT_PROMPT},
    ])])
    text, _ = dashscope_chat_completion(
        plus_model,
        openai_msgs,
        temperature=0.0,
        timeout=_video_timeout(),
    )
    allowed = text.startswith("是")
    return allowed, text


def _is_dashscope_download_error(exc: requests.HTTPError) -> bool:
    body = ""
    if exc.response is not None:
        body = (exc.response.text or "").lower()
    return "download" in body or "multimodal content" in body


def audit_video_url(url: str, *, image_audit_fn: Callable[[bytes], dict[str, Any]]) -> dict[str, Any]:
    """返回 {"url", "allow", "reason", "error"?}."""
    raw = (url or "").strip()
    if not raw:
        return {"url": url, "allow": True, "reason": "skip empty"}

    resolved = _resolve_video_url(raw)
    size = head_object_size(raw)
    use_frames = size is not None and size > _video_max_bytes()

    if not use_frames:
        try:
            allowed, text = _dashscope_video_audit(resolved)
            return {
                "url": raw,
                "allow": allowed,
                "reason": "" if allowed else (text or "视频不合规"),
            }
        except requests.HTTPError as exc:
            if not _is_dashscope_download_error(exc):
                logger.exception("[video_audit] DashScope 视频审核失败 url=%s", raw[:120])
                return {"url": raw, "allow": False, "reason": "视频审核服务异常", "error": True}
            logger.warning("[video_audit] DashScope 无法拉取 OSS 视频，改抽帧审核 url=%s", raw[:120])
            use_frames = True
        except Exception:
            logger.exception("[video_audit] DashScope 视频审核失败 url=%s", raw[:120])
            return {"url": raw, "allow": False, "reason": "视频审核服务异常", "error": True}

    frames = extract_audit_frames(resolved)
    if not frames:
        return {
            "url": raw,
            "allow": False,
            "reason": "视频无法访问（OSS 私有或文件过大），请检查 OSS 配置",
            "error": True,
        }

    for idx, frame_bytes in enumerate(frames):
        if not validate_image_bytes(frame_bytes):
            continue
        result = image_audit_fn(frame_bytes)
        if result.get("error"):
            return {
                "url": raw,
                "allow": False,
                "reason": result.get("reason", "视频帧审核异常"),
                "error": True,
            }
        if not result.get("allow"):
            return {
                "url": raw,
                "allow": False,
                "reason": result.get("reason") or f"视频第{idx + 1}帧不合规",
            }
    return {"url": raw, "allow": True, "reason": ""}
