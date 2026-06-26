"""Redis 存储帖子 RAG 向量与元数据."""

from __future__ import annotations

import json
import logging
import math
import re
from typing import Any

from clients.redis_client import redis_client
from config import settings

logger = logging.getLogger(__name__)

_RAG = settings.rag
IDS_KEY = _RAG.get("redis_article_ids_key", "forum_rag:article:ids")
DOC_KEY_PREFIX = _RAG.get("redis_article_doc_prefix", "forum_rag:article:")
_USER_IDS_KEY = _RAG.get("redis_user_ids_key", "forum_rag:user:ids")
_USER_DOC_PREFIX = _RAG.get("redis_user_doc_prefix", "forum_rag:user:")
VECTOR_SCAN_LIMIT = int(_RAG.get("vector_scan_limit", 800))


def _doc_key(article_id: int | str) -> str:
    return f"{DOC_KEY_PREFIX}{article_id}"


def _user_doc_key(user_id: int | str) -> str:
    return f"{_USER_DOC_PREFIX}{user_id}"


def save_article_index(
    article_id: int,
    *,
    doc: str,
    tags: list[str],
    media_type: int,
    embedding: list[float],
    video_url: str = "",
) -> None:
    key = _doc_key(article_id)
    payload = {
        "doc": doc[: int(_RAG.get("doc_truncate", 1200))],
        "tags": json.dumps(tags, ensure_ascii=False),
        "media_type": str(media_type),
        "embedding": json.dumps(embedding),
        "video_url": video_url or "",
    }
    pipe = redis_client.pipeline()
    pipe.hset(key, mapping=payload)
    pipe.sadd(IDS_KEY, str(article_id))
    pipe.execute()


def remove_article_index(article_id: int) -> None:
    pipe = redis_client.pipeline()
    pipe.delete(_doc_key(article_id))
    pipe.srem(IDS_KEY, str(article_id))
    pipe.execute()


def save_user_index(
    user_id: int,
    *,
    doc: str,
    embedding: list[float],
) -> None:
    key = _user_doc_key(user_id)
    payload = {
        "doc": doc[: int(_RAG.get("doc_truncate", 1200))],
        "embedding": json.dumps(embedding),
    }
    pipe = redis_client.pipeline()
    pipe.hset(key, mapping=payload)
    pipe.sadd(_USER_IDS_KEY, str(user_id))
    pipe.execute()


def remove_user_index(user_id: int) -> None:
    pipe = redis_client.pipeline()
    pipe.delete(_user_doc_key(user_id))
    pipe.srem(_USER_IDS_KEY, str(user_id))
    pipe.execute()


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na <= 0 or nb <= 0:
        return 0.0
    return dot / (na * nb)


def _user_profile_keyword_boost(query: str, doc: str) -> float:
    """仅昵称/用户名命中加分；不用地域扩展词，避免误召回无关用户."""
    q = (query or "").strip()
    if len(q) < 2 or not doc:
        return 0.0
    nick, name = "", ""
    for line in doc.split("\n"):
        line = line.strip()
        if line.startswith("昵称："):
            nick = line[3:].strip()
        elif line.startswith("用户名："):
            name = line[4:].strip()
    if not nick and not name:
        return 0.0
    if q in nick or q in name or q in f"{nick} {name}":
        return 0.35
    return 0.0


def _tag_keyword_boost(query: str, tags: list[str]) -> float:
    """入库检索词与搜索词字面匹配时加分（弥补短 query 向量相似度偏低）."""
    from rag.keyword_expand import expand_search_query

    q_blob = expand_search_query(query)
    if not q_blob:
        return 0.0
    q_tokens = [t for t in re.split(r"[\s；;,\n]+", q_blob) if len(t) >= 2]
    if not q_tokens:
        return 0.0
    tag_text = " ".join(str(t) for t in (tags or []))
    if not tag_text:
        return 0.0
    hit = sum(1 for t in q_tokens if t in tag_text)
    if hit <= 0:
        return 0.0
    return min(0.35, 0.12 * hit)


def vector_search_articles(
    query_vec: list[float],
    *,
    query_text: str = "",
    top_k: int = 80,
) -> list[dict[str, Any]]:
    """在 Redis 已索引帖子上做余弦相似度召回."""
    if not query_vec:
        return []
    try:
        ids = list(redis_client.smembers(IDS_KEY))[:VECTOR_SCAN_LIMIT]
    except Exception:
        logger.exception("读取 RAG 索引 ID 集合失败")
        return []
    min_sim = float(_RAG.get("vector_min_sim_floor", 0.04))
    scored: list[tuple[float, int]] = []
    for raw_id in ids:
        try:
            aid = int(raw_id)
        except (TypeError, ValueError):
            continue
        try:
            row = redis_client.hgetall(_doc_key(aid))
        except Exception:
            continue
        if not row:
            continue
        emb_raw = row.get("embedding") or row.get(b"embedding")
        if isinstance(emb_raw, bytes):
            emb_raw = emb_raw.decode("utf-8", errors="ignore")
        if not emb_raw:
            continue
        try:
            vec = json.loads(emb_raw)
        except json.JSONDecodeError:
            continue
        if not isinstance(vec, list):
            continue
        doc_vec = [float(x) for x in vec]
        if len(doc_vec) != len(query_vec):
            logger.warning(
                "[rag] 向量维度不一致 articleId=%s query_dim=%s doc_dim=%s，请重新索引该帖",
                aid,
                len(query_vec),
                len(doc_vec),
            )
            continue
        sim = _cosine(query_vec, doc_vec)
        if query_text:
            tags_raw = row.get("tags") or row.get(b"tags")
            if isinstance(tags_raw, bytes):
                tags_raw = tags_raw.decode("utf-8", errors="ignore")
            try:
                tags = json.loads(tags_raw) if tags_raw else []
            except json.JSONDecodeError:
                tags = []
            if isinstance(tags, list):
                sim += _tag_keyword_boost(query_text, tags)
        if sim > min_sim:
            scored.append((sim, aid))
    scored.sort(key=lambda x: x[0], reverse=True)
    top_k = min(top_k, int(_RAG.get("embedding_top_k", 80)))
    return [{"articleId": aid, "score": round(sim, 4)} for sim, aid in scored[:top_k]]


def vector_search_users(
    query_vec: list[float],
    *,
    query_text: str = "",
    top_k: int = 80,
) -> list[dict[str, Any]]:
    if not query_vec:
        return []
    try:
        ids = list(redis_client.smembers(_USER_IDS_KEY))[:VECTOR_SCAN_LIMIT]
    except Exception:
        logger.exception("读取用户 RAG 索引失败")
        return []
    min_sim = float(_RAG.get("vector_min_sim_floor", 0.04))
    scored: list[tuple[float, int]] = []
    for raw_id in ids:
        try:
            uid = int(raw_id)
        except (TypeError, ValueError):
            continue
        try:
            emb_raw = redis_client.hget(_user_doc_key(uid), "embedding")
        except Exception:
            continue
        if not emb_raw:
            continue
        if isinstance(emb_raw, bytes):
            emb_raw = emb_raw.decode("utf-8", errors="ignore")
        try:
            vec = json.loads(emb_raw)
        except json.JSONDecodeError:
            continue
        if not isinstance(vec, list):
            continue
        doc_vec = [float(x) for x in vec]
        if len(doc_vec) != len(query_vec):
            logger.warning(
                "[rag] 用户向量维度不一致 userId=%s query_dim=%s doc_dim=%s",
                uid,
                len(query_vec),
                len(doc_vec),
            )
            continue
        cosine = _cosine(query_vec, doc_vec)
        boost = 0.0
        if query_text and len(query_text) >= 2:
            doc_raw = redis_client.hget(_user_doc_key(uid), "doc")
            if isinstance(doc_raw, bytes):
                doc_raw = doc_raw.decode("utf-8", errors="ignore")
            boost = _user_profile_keyword_boost(query_text, doc_raw or "")
        sim = cosine + boost
        # 无昵称/用户名命中时，要求更高纯向量分，避免地域词误召回无关用户
        if boost > 0:
            if sim >= float(_RAG.get("vector_min_score_user_profile", 0.22)):
                scored.append((sim, uid))
        elif cosine >= float(_RAG.get("vector_min_score_user_semantic", 0.44)):
            scored.append((sim, uid))
    scored.sort(key=lambda x: x[0], reverse=True)
    top_k = min(top_k, int(_RAG.get("embedding_top_k", 80)))
    return [{"userId": uid, "score": round(sim, 4)} for sim, uid in scored[:top_k]]
