"""站点帮助语料：从 Java 公告中心动态拉取并缓存到 Redis."""

from __future__ import annotations

import logging
from typing import Any

from clients.forum_backend_client import list_published_notices
from clients.redis_client import redis_client
from config import settings

logger = logging.getLogger(__name__)

_CACHE_KEY = "mascot:site_help:corpus"

_NOTICE_KIND_LABEL: dict[int, str] = {
    0: "入站须知",
    1: "活动公告",
    2: "纪律公告",
    3: "系统更新",
    4: "版规公告",
}


def _cache_ttl() -> int:
    forum_ttl = settings.forum.get("site_help_cache_ttl")
    if forum_ttl is not None:
        return int(forum_ttl)
    return int(settings.cache.get("ttl", 86400))


def _max_notice_chars() -> int:
    return int(settings.forum.get("site_help_max_notice_chars", 4000))


def _truncate(text: str, limit: int) -> str:
    raw = (text or "").strip()
    if len(raw) <= limit:
        return raw
    return raw[:limit].rstrip() + "…"


def _notice_kind_label(raw: Any) -> str:
    try:
        kind = int(raw)
    except (TypeError, ValueError):
        return "公告"
    return _NOTICE_KIND_LABEL.get(kind, "公告")


def _format_notice(item: dict[str, Any]) -> str:
    title = str(item.get("title") or "未命名公告").strip()
    kind_label = _notice_kind_label(item.get("noticeKind"))
    subtitle = _truncate(str(item.get("subtitle") or ""), 300)
    body = _truncate(str(item.get("contentMarkdown") or ""), _max_notice_chars())
    lines = [f"## [{kind_label}] {title}"]
    if subtitle:
        lines.append(subtitle)
    if body:
        lines.append(body)
    return "\n".join(lines)


def _build_corpus_from_notices(notices: list[dict[str, Any]]) -> str:
    parts = [_format_notice(item) for item in notices if item]
    return "\n\n".join(part for part in parts if part.strip())


def _load_corpus_from_forum() -> str:
    notices = list_published_notices()
    corpus = _build_corpus_from_notices(notices)
    if corpus:
        return corpus
    return "暂无已发布公告，站点帮助内容请以后台公告中心为准。"


def _read_cached_corpus() -> str | None:
    try:
        cached = redis_client.get(_CACHE_KEY)
        if cached:
            return cached
    except Exception:
        logger.debug("读取站点帮助 Redis 缓存失败")
    return None


def _write_cached_corpus(corpus: str) -> None:
    try:
        redis_client.setex(_CACHE_KEY, _cache_ttl(), corpus)
    except Exception:
        logger.warning("站点帮助写入 Redis 失败，仅内存使用")


def ensure_site_help_cached() -> str:
    """优先读 Redis；过期或缺失时从 Java 公告中心刷新."""
    cached = _read_cached_corpus()
    if cached:
        return cached

    corpus = _load_corpus_from_forum()
    _write_cached_corpus(corpus)
    return corpus


def refresh_site_help_cache() -> str:
    """强制从 Java 拉取并覆盖缓存（启动预热或手动刷新）."""
    corpus = _load_corpus_from_forum()
    _write_cached_corpus(corpus)
    return corpus


def get_site_help_snippet() -> str:
    return ensure_site_help_cached()
