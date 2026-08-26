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
import warnings

import requests
from PIL import Image, ImageFile, UnidentifiedImageError

from config import settings
from utils.oss_media import get_object_bytes, object_key_from_public_url, presign_get_url

logger = logging.getLogger(__name__)

# 坏 EXIF / 截断 JPEG 常见于手机相册导出；允许加载像素，避免审图卡在警告或解析异常
ImageFile.LOAD_TRUNCATED_IMAGES = True

_IMG = settings.image
_ALLOWED = set(_IMG.get("allowed_formats", ["jpeg", "jpg", "png", "gif", "webp", "bmp"]))
_MAX_BYTES = int(_IMG.get("max_bytes", 10 * 1024 * 1024))


def _open_image(image_data: bytes) -> Image.Image:
    """打开图片并强制 load；屏蔽 PIL 损坏 EXIF 的 UserWarning。"""
    with warnings.catch_warnings():
        warnings.filterwarnings(
            "ignore",
            message=r"Corrupt EXIF data\..*",
            category=UserWarning,
            module=r"PIL\.TiffImagePlugin",
        )
        img = Image.open(io.BytesIO(image_data))
        img.load()
        return img


def validate_image_bytes(image_data: bytes) -> str | None:
    """返回小写格式名(jpeg/png/...); 不合法返回 None"""
    if not image_data:
        return None
    if len(image_data) > _MAX_BYTES:
        logger.warning("图片超出最大字节数 size=%d", len(image_data))
        return None
    try:
        with _open_image(image_data) as img:
            fmt = (img.format or "").lower()
    except (UnidentifiedImageError, OSError) as exc:
        logger.warning("图片无法识别: %s", exc)
        return None
    return fmt if fmt in _ALLOWED else None


def image_dimensions(image_data: bytes) -> tuple[int, int] | None:
    """读取图片宽高；无法解析时返回 None。"""
    if not image_data:
        return None
    try:
        with _open_image(image_data) as img:
            return int(img.size[0]), int(img.size[1])
    except (UnidentifiedImageError, OSError):
        return None


def meets_vision_model_min_size(image_data: bytes, min_side: int = 11) -> bool:
    """Dashscope 视觉模型要求宽高均大于 10 像素。"""
    dims = image_dimensions(image_data)
    if dims is None:
        return False
    width, height = dims
    return width >= min_side and height >= min_side


def to_data_url(image_data: bytes, fmt: str) -> str:
    b64 = base64.b64encode(image_data).decode("utf-8")
    return f"data:image/{fmt};base64,{b64}"


def fetch_image_bytes(url: str, timeout: int = 15, *, object_key: str | None = None) -> bytes | None:
    """从 OSS 拉图；优先 SDK 直读 object_key，否则 presign + HTTP."""
    if object_key:
        data = get_object_bytes(object_key)
        if data:
            return _limit_image_bytes(data, url or object_key)
    if not url:
        return None
    key = object_key or object_key_from_public_url(url)
    if key:
        for attempt in range(3):
            data = get_object_bytes(key)
            if data:
                return _limit_image_bytes(data, url)
            if attempt < 2:
                import time

                time.sleep(0.25 * (attempt + 1))
    try:
        fetch_url = presign_get_url(url)
        resp = requests.get(fetch_url, timeout=timeout, stream=True)
        resp.raise_for_status()
        data = resp.content
        return _limit_image_bytes(data, url)
    except Exception:
        logger.exception("拉取图片失败 url=%s key=%s", url, (key or "")[:80])
        return None


def _limit_image_bytes(data: bytes, source: str) -> bytes | None:
    if len(data) > _MAX_BYTES:
        logger.warning("远程图片超过大小限制 source=%s size=%d", source[:120], len(data))
        return None
    return data
