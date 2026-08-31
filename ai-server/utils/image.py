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


# 动图抽帧：视觉模型只解析首帧，首帧正常、后面帧违规就能整包过审。
# 逐帧送审会让成本线性翻倍（一个九图全 GIF 的表情包要 36 次调用），
# 改成抽样后拼成一张网格图，仍然只调一次
_ANIM_GRID_FRAMES = 4
_ANIM_CELL_SIZE = 512


def count_image_frames(image_data: bytes) -> int:
    """动图帧数；静态图或无法解析时返回 1。"""
    try:
        with _open_image(image_data) as img:
            return int(getattr(img, "n_frames", 1) or 1)
    except (UnidentifiedImageError, OSError, ValueError):
        return 1


def _pick_frame_indexes(total: int, want: int) -> list[int]:
    """等距采样，强制包含首帧和末帧——'首帧正常、末帧翻车'是最省事的藏法。"""
    if total <= want:
        return list(range(total))
    if want == 1:
        return [0]
    step = (total - 1) / (want - 1)
    return sorted({min(total - 1, round(i * step)) for i in range(want)})


def build_animation_grid(image_data: bytes) -> bytes | None:
    """把动图抽样帧拼成一张网格 PNG；拼不出来返回 None，由调用方退回首帧。"""
    try:
        with _open_image(image_data) as img:
            total = int(getattr(img, "n_frames", 1) or 1)
            if total <= 1:
                return None
            indexes = _pick_frame_indexes(total, _ANIM_GRID_FRAMES)
            cells = []
            for index in indexes:
                img.seek(index)
                frame = img.convert("RGB").copy()
                frame.thumbnail((_ANIM_CELL_SIZE, _ANIM_CELL_SIZE))
                cells.append(frame)
        if not cells:
            return None
        columns = 1 if len(cells) == 1 else 2
        rows = (len(cells) + columns - 1) // columns
        canvas = Image.new(
            "RGB", (columns * _ANIM_CELL_SIZE, rows * _ANIM_CELL_SIZE), (255, 255, 255)
        )
        for position, cell in enumerate(cells):
            col = position % columns
            row = position // columns
            offset_x = col * _ANIM_CELL_SIZE + (_ANIM_CELL_SIZE - cell.width) // 2
            offset_y = row * _ANIM_CELL_SIZE + (_ANIM_CELL_SIZE - cell.height) // 2
            canvas.paste(cell, (offset_x, offset_y))
        buffer = io.BytesIO()
        canvas.save(buffer, format="PNG")
        return buffer.getvalue()
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        logger.warning("动图抽帧拼接失败，退回首帧审核: %s", exc)
        return None


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
