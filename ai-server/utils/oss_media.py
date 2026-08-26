"""OSS 媒体 URL 解析与签名（供 DashScope / ffmpeg 拉取私有桶对象）."""
from __future__ import annotations

import logging
from urllib.parse import unquote, urlparse

import requests

from config import settings

logger = logging.getLogger(__name__)


def _oss_cfg() -> dict:
    return settings.oss or {}


def is_oss_configured() -> bool:
    cfg = _oss_cfg()
    bucket = (cfg.get("bucket_name") or "").strip()
    ak = (cfg.get("access_key_id") or "").strip()
    sk = (cfg.get("access_key_secret") or "").strip()
    return bool(bucket and ak and sk and bucket.lower() != "your-oss-bucket")


def object_key_from_public_url(url: str) -> str | None:
    """从 OSS 外链解析 object key；非本站 OSS 链接返回 None."""
    raw = (url or "").strip()
    if not raw:
        return None
    prefix = (_oss_cfg().get("url_prefix") or "").strip()
    if prefix and not prefix.endswith("/"):
        prefix += "/"
    if prefix and raw.startswith(prefix):
        key = raw[len(prefix):]
        return unquote(key.split("?", 1)[0])
    parsed = urlparse(raw)
    host = (parsed.netloc or "").lower()
    bucket = (_oss_cfg().get("bucket_name") or "").strip().lower()
    if bucket and host.startswith(f"{bucket}."):
        path = (parsed.path or "").lstrip("/")
        return unquote(path.split("?", 1)[0]) if path else None
    return None


def get_object_bytes(object_key: str) -> bytes | None:
    """通过 OSS SDK 直读对象（上传审图推荐，避免 HTTP 拉图偶发失败）。"""
    key = (object_key or "").strip().lstrip("/")
    if not key:
        return None
    if not is_oss_configured():
        logger.warning("[oss] 未配置 SDK 凭据，无法直读 key=%s", key[:80])
        return None
    try:
        import oss2

        cfg = _oss_cfg()
        auth = oss2.Auth(cfg["access_key_id"], cfg["access_key_secret"])
        endpoint = (cfg.get("endpoint") or "https://oss-cn-shenzhen.aliyuncs.com").strip()
        bucket = oss2.Bucket(auth, endpoint, cfg["bucket_name"])
        result = bucket.get_object(key)
        data = result.read()
        if not data:
            logger.warning("[oss] get_object 空 body key=%s", key[:80])
            return None
        logger.info("[oss] get_object ok key=%s bytes=%d", key[:80], len(data))
        return data
    except Exception:
        logger.exception("[oss] get_object 失败 key=%s", key[:80])
        return None


def presign_get_url(url: str, *, expires: int = 3600) -> str:
    """私有桶生成临时可读 URL；未配置 OSS 或解析失败则原样返回."""
    if not is_oss_configured():
        return url
    key = object_key_from_public_url(url)
    if not key:
        return url
    try:
        import oss2

        cfg = _oss_cfg()
        auth = oss2.Auth(cfg["access_key_id"], cfg["access_key_secret"])
        endpoint = (cfg.get("endpoint") or "https://oss-cn-shenzhen.aliyuncs.com").strip()
        bucket = oss2.Bucket(auth, endpoint, cfg["bucket_name"])
        signed = bucket.sign_url("GET", key, expires)
        logger.info("[oss] presign ok key=%s", key[:80])
        return signed
    except Exception:
        logger.exception("[oss] presign 失败 url=%s", url[:120])
        return url


def head_object_size(url: str, *, timeout: int = 15) -> int | None:
    """HEAD 对象大小（字节）；失败返回 None."""
    if not is_oss_configured():
        return None
    key = object_key_from_public_url(url)
    if not key:
        return None
    try:
        import oss2

        cfg = _oss_cfg()
        auth = oss2.Auth(cfg["access_key_id"], cfg["access_key_secret"])
        endpoint = (cfg.get("endpoint") or "https://oss-cn-shenzhen.aliyuncs.com").strip()
        bucket = oss2.Bucket(auth, endpoint, cfg["bucket_name"])
        meta = bucket.head_object(key)
        return int(meta.headers.get("Content-Length") or 0)
    except Exception:
        logger.warning("[oss] head_object 失败 key=%s", key[:80])
        return None


def probe_url_readable(url: str, *, timeout: int = 15) -> bool:
    """检测 URL 是否可被本服务 GET/HEAD（不拉全量 body）."""
    if not url:
        return False
    try:
        r = requests.head(url, timeout=timeout, allow_redirects=True)
        if r.status_code == 405:
            r = requests.get(url, timeout=timeout, stream=True, headers={"Range": "bytes=0-0"})
        return r.status_code in (200, 206)
    except Exception:
        logger.warning("[oss] probe 不可读 url=%s", url[:120])
        return False
