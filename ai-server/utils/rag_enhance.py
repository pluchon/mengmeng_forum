"""
RAG 检索增强：关键词重叠 + 可选向量预筛 + TextReRank 融合排序.
站内搜索默认走 light 模式，减少 embedding/rerank 调用以适配 2C4G 小机.
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
    seen: set[str] = set()
    out: list[str] = []
    for t in tokens:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out[:12]


def _expanded_match_tokens(query: str) -> list[str]:
    """原词 + 同义扩展 + 分词，用于字面倒排与相似度预筛."""
    from rag.keyword_expand import expand_search_term_list

    seen: set[str] = set()
    out: list[str] = []
    for term in expand_search_term_list(query):
        for t in tokenize_query(term):
            if t not in seen:
                seen.add(t)
                out.append(t)
    return out[:16]


def _min_keyword_floor(query: str, base: float) -> float:
    """短中文 query 提高字面门槛，缓解同形异义词误召回."""
    q = (query or "").strip()
    if len(q) == 2 and q and all("\u4e00" <= c <= "\u9fff" for c in q):
        return base + 0.10
    return base


def keyword_overlap_score(query: str, document: str) -> float:
    tokens = _expanded_match_tokens(query)
    if not tokens:
        return 0.0
    doc = (document or "").lower()
    if not doc:
        return 0.0
    hit = sum(1 for t in tokens if t in doc)
    base = hit / len(tokens)
    q = query.strip().lower()
    if len(q) >= 2 and q in doc:
        base = min(1.0, base + 0.25)
    for term in tokens:
        if len(term) >= 2 and term in doc:
            base = min(1.0, base + 0.05)
    return min(1.0, base)


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
    cap = min(top_n, len(documents), int(_RAG.get("light_rerank_top_n", 30)))
    try:
        resp = TextReRank.call(
            model=rerank_model_name(),
            query=query,
            documents=documents,
            top_n=cap,
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
    light: bool = False,
) -> list[dict[str, Any]]:
    """
    融合关键词、向量与 rerank，输出 [{id_key, score}, ...].
    light=True：站内搜索用，跳过 embedding，候选少时跳过 rerank.
    """
    if not query.strip() or not documents:
        return []

    threshold = float(threshold if threshold is not None else _RAG.get("relevance_threshold", 0.12))
    top_n = int(top_n if top_n is not None else _RAG.get("top_n", 50))
    emb_top_k = int(_RAG.get("embedding_top_k", 80))
    min_kw = _min_keyword_floor(query, float(_RAG.get("min_keyword_overlap", 0.08)))

    if light:
        emb_w = 0.0
        skip_below = int(_RAG.get("skip_rerank_below", 5))
        if len(documents) <= skip_below:
            rw, kw_w = 0.0, 1.0
        else:
            rw = float(_RAG.get("light_rerank_weight", 0.55))
            kw_w = float(_RAG.get("light_keyword_weight", 0.45))
    else:
        rw = float(_RAG.get("rerank_weight", 0.72))
        kw_w = float(_RAG.get("keyword_weight", 0.18))
        emb_w = float(_RAG.get("embedding_weight", 0.10))

    kw_scores = [keyword_overlap_score(query, d) for d in documents]
    kw_norm = _normalize_scores(kw_scores)

    emb_scores: list[float] | None = None
    if emb_w > 0:
        emb_raw = embedding_similarities(query, documents)
        if emb_raw is not None:
            emb_scores = _normalize_scores(emb_raw)

    rerank_by_idx: dict[int, float] = {}
    rerank_list = [0.0] * len(documents)
    if rw > 0:
        rerank_pairs = rerank_documents(
            query,
            documents,
            top_n=max(top_n, emb_top_k),
        )
        rerank_by_idx = {idx: sc for idx, sc in rerank_pairs}
        rerank_list = [rerank_by_idx.get(i, 0.0) for i in range(len(documents))]
    rerank_norm = _normalize_scores(rerank_list) if rw > 0 else rerank_list

    fused: list[tuple[int, float]] = []
    for i in range(len(documents)):
        rr = rerank_norm[i] if rw > 0 else 0.0
        kw = kw_norm[i]
        emb = emb_scores[i] if emb_scores is not None else 0.0
        if emb_scores is None:
            total_w = rw + kw_w
            score = (rw * rr + kw_w * kw) / total_w if total_w > 0 else kw
        else:
            score = rw * rr + kw_w * kw + emb_w * emb
        raw_kw = kw_scores[i]
        if rw <= 0 and raw_kw < min_kw:
            continue
        if rw > 0 and rerank_by_idx.get(i) is None and raw_kw < min_kw and emb_scores is None:
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
