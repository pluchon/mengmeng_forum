"""
图片处理工具.
- 校验 PIL 可识别; 返回格式名
- 把 bytes 转 data URL 喂给视觉模型
- 从 URL 拉图; 用于审核流程拉取已上传到 OSS 的封面 / 相册图
"""
from __future__ import annotations

import base64
import io
import logging
from typing import Optional

import requests
from PIL import Image, UnidentifiedImageError

from config import settings

logger = logging.getLogger(__name__)

_IMG = settings.image
_ALLOWED = set(_IMG.get("allowed_formats", ["jpeg", "jpg", "png", "gif", "webp", "bmp"]))
_MAX_BYTES = int(_IMG.get("max_bytes", 10 * 1024 * 1024))


def validate_image_bytes(image_data: bytes) -> Optional[str]:
    """返回小写格式名(jpeg/png/...); 不合法返回 None"""
    if not image_data:
        return None
    if len(image_data) > _MAX_BYTES:
        logger.warning("图片超出最大字节数 size=%d", len(image_data))
        return None
    try:
        with Image.open(io.BytesIO(image_data)) as img:
            fmt = (img.format or "").lower()
    except (UnidentifiedImageError, OSError):
        return None
    return fmt if fmt in _ALLOWED else None


def to_data_url(image_data: bytes, fmt: str) -> str:
    b64 = base64.b64encode(image_data).decode("utf-8")
    return f"data:image/{fmt};base64,{b64}"


def fetch_image_bytes(url: str, timeout: int = 15) -> bytes | None:
    """从 OSS 拉图；私有桶会先签名再拉取."""
    if not url:
        return None
    try:
        from utils.oss_media import presign_get_url

        fetch_url = presign_get_url(url)
        resp = requests.get(fetch_url, timeout=timeout, stream=True)
        resp.raise_for_status()
        data = resp.content
        if len(data) > _MAX_BYTES:
            logger.warning("远程图片超过大小限制 url=%s size=%d", url, len(data))
            return None
        return data
    except Exception:
        logger.exception("拉取图片失败 url=%s", url)
        return None
