"""RAG 向量召回与候选融合服务."""
from __future__ import annotations

from typing import Any

from clients.dashscope_embedding import embed_query
from config import settings
from rag.store import vector_search_articles, vector_search_users
from utils.rag_enhance import hybrid_rank

_RAG = settings.rag


def clean_query(raw: Any) -> str:
    """清洗检索词并限制长度，避免无界 embedding 输入."""
    max_len = int(_RAG.get("query_max_len", 500))
    return str(raw or "").strip()[:max_len]


def search_articles_by_vector(
    query: str,
    candidates: list[Any] | None,
) -> tuple[list[dict[str, Any]], str]:
    """帖子向量召回；存在候选文本时沿用原融合 rerank 逻辑."""
    qvec = embed_query(query)
    if not qvec:
        return [], "embedding unavailable"

    vector_hits = vector_search_articles(
        qvec,
        query_text=query,
        top_k=int(_RAG.get("embedding_top_k", 80)),
    )
    if not vector_hits:
        return [], "no index hits"

    min_vec = float(_RAG.get("vector_min_score", 0.12))
    vector_hits = [h for h in vector_hits if float(h.get("score") or 0) >= min_vec]
    if not vector_hits:
        return [], "below threshold"

    if candidates:
        id_to_text: dict[Any, str] = {}
        for candidate in candidates:
            if isinstance(candidate, dict) and candidate.get("articleId") is not None:
                id_to_text[candidate["articleId"]] = str(candidate.get("text") or "").strip()

        docs: list[str] = []
        meta: list[Any] = []
        for hit in vector_hits:
            article_id = hit["articleId"]
            text = id_to_text.get(article_id) or id_to_text.get(str(article_id)) or ""
            if not text:
                continue
            docs.append(text[: int(_RAG.get("doc_truncate", 1200))])
            meta.append(article_id)
        if docs:
            return hybrid_rank(query, docs, meta, id_key="articleId"), "success"

    return vector_hits, "vector_only"


def search_users_by_vector(query: str) -> tuple[list[dict[str, Any]], str]:
    """用户向量召回."""
    qvec = embed_query(query)
    if not qvec:
        return [], "embedding unavailable"

    vector_hits = vector_search_users(
        qvec,
        query_text=query,
        top_k=int(_RAG.get("embedding_top_k", 80)),
    )
    if not vector_hits:
        return [], "no index hits"

    min_vec = float(_RAG.get("vector_min_score_user", 0.22))
    vector_hits = [h for h in vector_hits if float(h.get("score") or 0) >= min_vec]
    return vector_hits, "vector_only"
