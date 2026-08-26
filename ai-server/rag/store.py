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
_EMOJI_IDS_KEY = _RAG.get("redis_emoji_ids_key", "forum_rag:emoji:ids")
_EMOJI_DOC_PREFIX = _RAG.get("redis_emoji_doc_prefix", "forum_rag:emoji:")
_MUSIC_IDS_KEY = _RAG.get("redis_music_ids_key", "forum_rag:music:ids")
_MUSIC_DOC_PREFIX = _RAG.get("redis_music_doc_prefix", "forum_rag:music:")
VECTOR_SCAN_LIMIT = int(_RAG.get("vector_scan_limit", 800))


def _take_scan_ids(raw_ids: list[Any], *, index_name: str) -> list[Any]:
    """截断扫描集合；超限时打 warning，避免静默丢召回。"""
    total = len(raw_ids)
    if total > VECTOR_SCAN_LIMIT:
        logger.warning(
            "[rag] %s 索引量=%s 已超 vector_scan_limit=%s，召回可能不完整",
            index_name,
            total,
            VECTOR_SCAN_LIMIT,
        )
    return raw_ids[:VECTOR_SCAN_LIMIT]


def _doc_key(article_id: int | str) -> str:
    return f"{DOC_KEY_PREFIX}{article_id}"


def _user_doc_key(user_id: int | str) -> str:
    return f"{_USER_DOC_PREFIX}{user_id}"


def _emoji_doc_key(shop_id: int | str) -> str:
    return f"{_EMOJI_DOC_PREFIX}{shop_id}"


def _music_doc_key(music_key: str) -> str:
    return f"{_MUSIC_DOC_PREFIX}{music_key}"


def save_article_index(
    article_id: int,
    *,
    doc: str,
    tags: list[str],
    media_type: int,
    embedding: list[float],
    video_url: str = "",
    embedding_model: str = "",
) -> None:
    key = _doc_key(article_id)
    payload = {
        "doc": doc[: int(_RAG.get("doc_truncate", 1200))],
        "tags": json.dumps(tags, ensure_ascii=False),
        "media_type": str(media_type),
        "embedding": json.dumps(embedding),
        "video_url": video_url or "",
        "embedding_model": embedding_model or "",
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
    embedding_model: str = "",
) -> None:
    key = _user_doc_key(user_id)
    payload = {
        "doc": doc[: int(_RAG.get("doc_truncate", 1200))],
        "embedding": json.dumps(embedding),
        "embedding_model": embedding_model or "",
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


def save_emoji_index(
    shop_id: int,
    *,
    doc: str,
    embedding: list[float],
    embedding_model: str = "",
) -> None:
    pipe = redis_client.pipeline()
    pipe.hset(
        _emoji_doc_key(shop_id),
        mapping={
            "doc": doc[: int(_RAG.get("doc_truncate", 1200))],
            "embedding": json.dumps(embedding),
            "embedding_model": embedding_model or "",
        },
    )
    pipe.sadd(_EMOJI_IDS_KEY, str(shop_id))
    pipe.execute()


def save_music_index(
    music_key: str,
    *,
    doc: str,
    embedding: list[float],
    embedding_model: str = "",
) -> None:
    key = str(music_key or "").strip()
    if not key:
        raise ValueError("musicKey required")
    pipe = redis_client.pipeline()
    pipe.hset(
        _music_doc_key(key),
        mapping={
            "doc": doc[: int(_RAG.get("doc_truncate", 1200))],
            "embedding": json.dumps(embedding),
            "embedding_model": embedding_model or "",
        },
    )
    pipe.sadd(_MUSIC_IDS_KEY, key)
    pipe.execute()


def remove_music_index(music_key: str) -> None:
    key = str(music_key or "").strip()
    if not key:
        return
    pipe = redis_client.pipeline()
    pipe.delete(_music_doc_key(key))
    pipe.srem(_MUSIC_IDS_KEY, key)
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


def _as_text(value: Any) -> str:
    """Redis 返回值可能是 bytes 或 str，统一解码为 str."""
    if value is None:
        return ""
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="ignore")
    return str(value)


def _decode_embedding(raw: Any) -> list[float] | None:
    """解析入库的 JSON 向量；空值/非法/非列表一律返回 None."""
    text = _as_text(raw)
    if not text:
        return None
    try:
        vec = json.loads(text)
    except json.JSONDecodeError:
        return None
    if not isinstance(vec, list):
        return None
    try:
        return [float(x) for x in vec]
    except (TypeError, ValueError):
        return None


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
        ids = _take_scan_ids(list(redis_client.smembers(IDS_KEY)), index_name="article")
    except Exception:
        logger.exception("读取 RAG 索引 ID 集合失败")
        return []
    # 先归一化合法 articleId，再用 pipeline 批量取 doc，避免逐条 round-trip
    aids: list[int] = []
    for raw_id in ids:
        try:
            aids.append(int(raw_id))
        except (TypeError, ValueError):
            continue
    if not aids:
        return []
    try:
        pipe = redis_client.pipeline()
        for aid in aids:
            pipe.hgetall(_doc_key(aid))
        rows = pipe.execute()
    except Exception:
        logger.exception("批量读取帖子 RAG 条目失败")
        return []
    min_sim = float(_RAG.get("vector_min_sim_floor", 0.04))
    scored: list[tuple[float, int]] = []
    for aid, row in zip(aids, rows):
        if not row:
            continue
        doc_vec = _decode_embedding(row.get("embedding") or row.get(b"embedding"))
        if doc_vec is None:
            continue
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
            tags_text = _as_text(row.get("tags") or row.get(b"tags"))
            try:
                tags = json.loads(tags_text) if tags_text else []
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
        ids = _take_scan_ids(list(redis_client.smembers(_USER_IDS_KEY)), index_name="user")
    except Exception:
        logger.exception("读取用户 RAG 索引失败")
        return []
    uids: list[int] = []
    for raw_id in ids:
        try:
            uids.append(int(raw_id))
        except (TypeError, ValueError):
            continue
    if not uids:
        return []
    # 每个用户同时取 embedding 与 doc（昵称/用户名加分用），一次 pipeline 批量拉取
    try:
        pipe = redis_client.pipeline()
        for uid in uids:
            pipe.hget(_user_doc_key(uid), "embedding")
            pipe.hget(_user_doc_key(uid), "doc")
        results = pipe.execute()
    except Exception:
        logger.exception("批量读取用户 RAG 条目失败")
        return []
    scored: list[tuple[float, int]] = []
    for idx, uid in enumerate(uids):
        emb_raw = results[idx * 2]
        doc_raw = results[idx * 2 + 1]
        doc_vec = _decode_embedding(emb_raw)
        if doc_vec is None:
            continue
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
            boost = _user_profile_keyword_boost(query_text, _as_text(doc_raw))
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


def vector_search_emojis(query_vec: list[float], *, top_k: int = 80) -> list[dict[str, Any]]:
    if not query_vec:
        return []
    try:
        ids = _take_scan_ids(list(redis_client.smembers(_EMOJI_IDS_KEY)), index_name="emoji")
    except Exception:
        logger.exception("读取表情包 RAG 索引失败")
        return []
    shop_ids: list[int] = []
    for raw_id in ids:
        try:
            shop_ids.append(int(raw_id))
        except (TypeError, ValueError):
            continue
    if not shop_ids:
        return []
    try:
        pipe = redis_client.pipeline()
        for shop_id in shop_ids:
            pipe.hget(_emoji_doc_key(shop_id), "embedding")
        embeddings = pipe.execute()
    except Exception:
        logger.exception("批量读取表情包 RAG 条目失败")
        return []
    min_sim = float(_RAG.get("vector_min_sim_floor", 0.04))
    scored: list[tuple[float, int]] = []
    for shop_id, emb_raw in zip(shop_ids, embeddings):
        doc_vec = _decode_embedding(emb_raw)
        if doc_vec is None:
            continue
        similarity = _cosine(query_vec, doc_vec)
        if similarity >= min_sim:
            scored.append((similarity, shop_id))
    scored.sort(key=lambda item: item[0], reverse=True)
    return [{"shopId": shop_id, "score": round(score, 4)} for score, shop_id in scored[:top_k]]


def vector_search_musics(query_vec: list[float], *, top_k: int = 80) -> list[dict[str, Any]]:
    if not query_vec:
        return []
    try:
        ids = _take_scan_ids(list(redis_client.smembers(_MUSIC_IDS_KEY)), index_name="music")
    except Exception:
        logger.exception("读取曲目 RAG 索引失败")
        return []
    music_keys: list[str] = []
    for raw_id in ids:
        key = _as_text(raw_id).strip()
        if key:
            music_keys.append(key)
    if not music_keys:
        return []
    try:
        pipe = redis_client.pipeline()
        for music_key in music_keys:
            pipe.hget(_music_doc_key(music_key), "embedding")
        embeddings = pipe.execute()
    except Exception:
        logger.exception("批量读取曲目 RAG 条目失败")
        return []
    min_sim = float(_RAG.get("vector_min_sim_floor", 0.04))
    scored: list[tuple[float, str]] = []
    for music_key, emb_raw in zip(music_keys, embeddings):
        doc_vec = _decode_embedding(emb_raw)
        if doc_vec is None:
            continue
        similarity = _cosine(query_vec, doc_vec)
        if similarity >= min_sim:
            scored.append((similarity, music_key))
    scored.sort(key=lambda item: item[0], reverse=True)
    return [{"musicKey": music_key, "score": round(score, 4)} for score, music_key in scored[:top_k]]
