"""重建走 text-embedding 降级路径写入的 Redis 向量（v3 → v4 后需重算）。

用法（在 ai-server 目录）:
  python -m rag.rebuild_text_fallback

仅当索引条目缺少 embedding_model，或 embedding_model 以 text-embedding-v3 开头时重算。
多模态主索引（qwen3-vl-embedding）会跳过。
"""

from __future__ import annotations

import json
import logging
import sys

from clients.dashscope_embedding import embed_texts
from clients.llm import embedding_text_fallback_model_name
from clients.redis_client import redis_client
from config import settings

logger = logging.getLogger(__name__)

_RAG = settings.rag
_TARGETS = [
    (
        _RAG.get("redis_article_ids_key", "forum_rag:article:ids"),
        _RAG.get("redis_article_doc_prefix", "forum_rag:article:"),
    ),
    (
        _RAG.get("redis_user_ids_key", "forum_rag:user:ids"),
        _RAG.get("redis_user_doc_prefix", "forum_rag:user:"),
    ),
    (
        _RAG.get("redis_emoji_ids_key", "forum_rag:emoji:ids"),
        _RAG.get("redis_emoji_doc_prefix", "forum_rag:emoji:"),
    ),
]


def _needs_rebuild(model: str | None) -> bool:
    # 仅重建明确走 text-embedding-v3 降级的条目；多模态与未打标历史条目跳过
    if not model:
        return False
    return model.startswith("text-embedding-v3")


def _rebuild_one(key: str, current_model: str) -> bool:
    raw = redis_client.hgetall(key)
    if not raw:
        return False
    # redis-py 可能返回 bytes
    def _s(v: object) -> str:
        if v is None:
            return ""
        if isinstance(v, bytes):
            return v.decode("utf-8", errors="ignore")
        return str(v)

    model = _s(raw.get(b"embedding_model") or raw.get("embedding_model"))
    if not _needs_rebuild(model):
        return False

    doc = _s(raw.get(b"doc") or raw.get("doc")).strip()
    if not doc:
        logger.warning("skip empty doc key=%s", key)
        return False

    vecs = embed_texts([doc])
    if not vecs or not vecs[0]:
        logger.warning("rebuild failed embed key=%s", key)
        return False

    redis_client.hset(
        key,
        mapping={
            "embedding": json.dumps(vecs[0]),
            "embedding_model": current_model,
        },
    )
    return True


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    current = embedding_text_fallback_model_name()
    logger.info("text fallback model=%s", current)
    total = 0
    rebuilt = 0
    for ids_key, prefix in _TARGETS:
        ids = redis_client.smembers(ids_key) or set()
        logger.info("scan %s count=%s", ids_key, len(ids))
        for mid in ids:
            sid = mid.decode("utf-8") if isinstance(mid, bytes) else str(mid)
            key = f"{prefix}{sid}"
            total += 1
            try:
                if _rebuild_one(key, current):
                    rebuilt += 1
                    logger.info("rebuilt %s", key)
            except Exception:
                logger.exception("error rebuilding %s", key)
    logger.info("done scanned=%s rebuilt=%s", total, rebuilt)
    return 0


if __name__ == "__main__":
    sys.exit(main())
