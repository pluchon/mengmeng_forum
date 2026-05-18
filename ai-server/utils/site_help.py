"""站点帮助 RAG 缓存：启动时加载 details/*.txt 到 Redis。"""
from __future__ import annotations

import logging
from pathlib import Path

from clients.redis_client import redis_client
from config import settings

logger = logging.getLogger(__name__)

_CACHE_KEY = "mascot:site_help:corpus"
_DETAILS_DIR = Path(__file__).resolve().parent.parent / "details"

_TOPIC_FILES = {
    "如何发帖": "如何发帖.txt",
    "积分规则": "积分规则.txt",
    "VIP权益": "VIP权益.txt",
    "版规摘要": "版规摘要.txt",
}


def _load_corpus_from_disk() -> str:
    parts: list[str] = []
    for title, fname in _TOPIC_FILES.items():
        path = _DETAILS_DIR / fname
        if not path.is_file():
            logger.warning("站点帮助文档缺失: %s", path)
            continue
        text = path.read_text(encoding="utf-8").strip()
        if text:
            parts.append(f"## {title}\n{text}")
    return "\n\n".join(parts)


def ensure_site_help_cached() -> str:
    """若 Redis 无缓存则从 details 目录加载。"""
    try:
        cached = redis_client.get(_CACHE_KEY)
        if cached:
            return cached
    except Exception:
        logger.debug("读取站点帮助缓存失败，改从磁盘加载")

    corpus = _load_corpus_from_disk()
    if not corpus:
        corpus = "暂无站点帮助文档，请联系管理员配置 ai-server/details 下的说明文件。"
    try:
        ttl = int(settings.cache.get("ttl", 86400))
        redis_client.setex(_CACHE_KEY, ttl, corpus)
    except Exception:
        logger.warning("站点帮助写入 Redis 失败，仅内存使用")
    return corpus


def get_site_help_snippet() -> str:
    return ensure_site_help_cached()
