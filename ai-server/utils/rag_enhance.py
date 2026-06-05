"""
RAG 检索增强：关键词重叠 + 可选向量预筛 + TextReRank 融合排序.
"""

from __future__ import annotations

import logging
import re
from typing import Any

from dashscope import TextReRank

from clients.dashscope_embedding import embedding_similarities
from clients.llm import dashscope_api_key, rerank_model_name
from config import settings

logger = logging.getLogger(__name__)

_RAG = settings.rag


def _field(obj, key: str, default=None):
    if obj is None:
        return default
    if isinstance(obj, dict):
        return obj.get(key, default)
    return getattr(obj, key, default)
_TOKEN_RE = re.compile(r"[\u4e00-\u9fa5]{2,}|[a-zA-Z0-9_]{2,}")


def tokenize_query(query: str) -> list[str]:
    q = (query or "").strip().lower()
    if not q:
        return []
    tokens = [t for t in _TOKEN_RE.findall(q)]
    if not tokens and len(q) >= 1:
        tokens = [q]
    # 去重保序
    seen: set[str] = set()
    out: list[str] = []
    for t in tokens:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out[:12]


def keyword_overlap_score(query: str, document: str) -> float:
    tokens = tokenize_query(query)
    if not tokens:
        return 0.0
    doc = (document or "").lower()
    if not doc:
        return 0.0
    hit = sum(1 for t in tokens if t in doc)
    base = hit / len(tokens)
    # 完整 query 子串额外加分
    q = query.strip().lower()
    if len(q) >= 2 and q in doc:
        base = min(1.0, base + 0.25)
    return base


def _normalize_scores(scores: list[float]) -> list[float]:
    if not scores:
        return scores
    lo, hi = min(scores), max(scores)
    if hi <= lo:
        return [1.0 if s > 0 else 0.0 for s in scores]
    return [(s - lo) / (hi - lo) for s in scores]


def rerank_documents(query: str, documents: list[str], *, top_n: int) -> list[tuple[int, float]]:
    """返回 (doc_index, relevance_score) 列表."""
    if not documents:
        return []
    try:
        resp = TextReRank.call(
            model=rerank_model_name(),
            query=query,
            documents=documents,
            top_n=min(top_n, len(documents)),
            api_key=dashscope_api_key(),
        )
    except Exception:
        logger.exception("RAG rerank 调用异常")
        return []

    output = _field(resp, "output")
    results = _field(output, "results") or []
    if resp.status_code != 200 or not results:
        return []

    out: list[tuple[int, float]] = []
    for item in results:
        idx = _field(item, "index")
        score = float(_field(item, "relevance_score") or 0.0)
        if idx is None or idx < 0 or idx >= len(documents):
            continue
        out.append((int(idx), score))
    return out


def hybrid_rank(
    query: str,
    documents: list[str],
    meta_ids: list[Any],
    *,
    id_key: str,
    threshold: float | None = None,
    top_n: int | None = None,
) -> list[dict[str, Any]]:
    """
    融合关键词、向量与 rerank，输出 [{id_key, score}, ...].
    meta_ids 与 documents 一一对应.
    """
    if not query.strip() or not documents:
        return []

    threshold = float(threshold if threshold is not None else _RAG.get("relevance_threshold", 0.12))
    top_n = int(top_n if top_n is not None else _RAG.get("top_n", 50))
    rw = float(_RAG.get("rerank_weight", 0.72))
    kw_w = float(_RAG.get("keyword_weight", 0.18))
    emb_w = float(_RAG.get("embedding_weight", 0.10))
    emb_top_k = int(_RAG.get("embedding_top_k", 80))
    min_kw = float(_RAG.get("min_keyword_overlap", 0.08))

    kw_scores = [keyword_overlap_score(query, d) for d in documents]
    kw_norm = _normalize_scores(kw_scores)

    emb_scores: list[float] | None = None
    if emb_w > 0:
        emb_raw = embedding_similarities(query, documents)
        if emb_raw is not None:
            emb_scores = _normalize_scores(emb_raw)

    rerank_pairs = rerank_documents(query, documents, top_n=max(top_n, emb_top_k))
    rerank_by_idx: dict[int, float] = {idx: sc for idx, sc in rerank_pairs}
    rerank_list = [rerank_by_idx.get(i, 0.0) for i in range(len(documents))]
    rerank_norm = _normalize_scores(rerank_list)

    fused: list[tuple[int, float]] = []
    for i in range(len(documents)):
        rr = rerank_norm[i]
        kw = kw_norm[i]
        emb = emb_scores[i] if emb_scores is not None else 0.0
        if emb_scores is None:
            total_w = rw + kw_w
            score = (rw * rr + kw_w * kw) / total_w if total_w > 0 else rr
        else:
            score = rw * rr + kw_w * kw + emb_w * emb
        # 无 rerank 命中时，关键词足够高仍可入选
        if rerank_by_idx.get(i) is None and kw < min_kw and emb_scores is None:
            continue
        fused.append((i, score))

    fused.sort(key=lambda x: x[1], reverse=True)

    out: list[dict[str, Any]] = []
    seen: set[Any] = set()
    for idx, score in fused[:top_n]:
        if score < threshold:
            continue
        mid = meta_ids[idx]
        if mid in seen:
            continue
        seen.add(mid)
        out.append({id_key: mid, "score": round(score, 4)})
    return out
