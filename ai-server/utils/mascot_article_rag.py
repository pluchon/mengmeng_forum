"""看板娘：站内帖子向量检索（qwen3-vl-embedding + Redis 索引）."""

from __future__ import annotations

import logging
import re
from typing import Any

from clients.dashscope_embedding import embed_query
from config import settings
from rag.store import vector_search_articles
from utils.rag_enhance import hybrid_rank

logger = logging.getLogger(__name__)

MAX_RELATED = 5


def _min_score() -> float:
    return float(settings.rag.get("mascot_min_score", 0.42))


def _relative_to_top() -> float:
    return float(settings.rag.get("mascot_relative_to_top", 0.93))


def _top_min_to_show() -> float:
    return float(settings.rag.get("mascot_top_min_to_show", 0.46))


def _score_gap_max() -> float:
    return float(settings.rag.get("mascot_score_gap", 0.055))


def _high_score_bypass() -> float:
    return float(settings.rag.get("mascot_high_score_bypass", 0.52))


def _tokenize_query(query: str) -> list[str]:
    parts = re.split(r"[\s,，、；;|/\\]+", (query or "").strip())
    out: list[str] = []
    for p in parts:
        t = p.strip()
        if len(t) < 2 or t in out:
            continue
        out.append(t)
        if len(out) >= 8:
            break
    return out


def _passes_semantic_gate(query: str, title: str, score: float) -> bool:
    if score >= _high_score_bypass():
        return True
    title = (title or "").strip()
    if not title:
        return False
    tokens = _tokenize_query(query)
    if not tokens:
        return score >= _top_min_to_show()
    title_lower = title.lower()
    return any(tok.lower() in title_lower for tok in tokens)


def _apply_relevance_cutoff(query: str, ranked: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """按绝对阈值 + 相对 top + 分差截断，0~5 条均可."""
    if not ranked:
        return []
    floor = _min_score()
    sorted_rows = sorted(ranked, key=lambda x: float(x.get("score") or 0), reverse=True)
    eligible = [r for r in sorted_rows if float(r.get("score") or 0) >= floor]
    if not eligible:
        return []
    top = float(eligible[0].get("score") or 0)
    if top < _top_min_to_show():
        return []
    relative_floor = max(floor, top * _relative_to_top())
    out: list[dict[str, Any]] = []
    prev_score = -1.0
    gap = _score_gap_max()
    for row in eligible:
        if len(out) >= MAX_RELATED:
            break
        score = float(row.get("score") or 0)
        if score < relative_floor:
            break
        title = str(row.get("title") or "")
        if not _passes_semantic_gate(query, title, score):
            continue
        if prev_score >= 0 and prev_score - score > gap:
            break
        prev_score = score
        out.append(row)
    return out


def fetch_related_articles(query: str, *, candidates: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
    """
    返回 [{articleId, title?, score}, ...] 按 score 降序，最多 5 条。
    title 仅当 candidates 含 text 且能 hybrid_rank 时由调用方补全；此处只保证 id+score。
    """
    q = (query or "").strip()
    if len(q) < 2:
        return []

    qvec = embed_query(q)
    if not qvec:
        return []

    top_k = int(settings.rag.get("embedding_top_k", 80))
    hits = vector_search_articles(qvec, query_text=q, top_k=top_k)
    if not hits:
        return []

    min_vec = float(settings.rag.get("vector_min_score", 0.12))
    hits = [h for h in hits if float(h.get("score") or 0) >= min_vec]
    if not hits:
        return []

    ranked: list[dict[str, Any]] = []
    if candidates:
        id_to_text = {}
        for c in candidates:
            if isinstance(c, dict) and c.get("articleId") is not None:
                id_to_text[c["articleId"]] = (c.get("text") or "").strip()
                id_to_text[str(c["articleId"])] = id_to_text[c["articleId"]]
        docs, meta = [], []
        for h in hits:
            aid = h["articleId"]
            text = id_to_text.get(aid) or id_to_text.get(str(aid)) or ""
            if not text:
                continue
            docs.append(text[: int(settings.rag.get("doc_truncate", 1200))])
            meta.append(aid)
        if docs:
            ranked = hybrid_rank(q, docs, meta, id_key="articleId")

    if not ranked:
        ranked = [
            {"articleId": h["articleId"], "score": float(h.get("score") or 0)}
            for h in hits
        ]

    out: list[dict[str, Any]] = []
    for row in _apply_relevance_cutoff(q, ranked):
        aid = row.get("articleId")
        if aid is None:
            continue
        score = float(row.get("score") or 0)
        out.append({"articleId": aid, "score": round(score, 4)})
    return out


def format_related_for_prompt(articles: list[dict[str, Any]]) -> str:
    if not articles:
        return ""
    lines = []
    for i, a in enumerate(articles[:MAX_RELATED], 1):
        aid = a.get("articleId")
        title = (a.get("title") or "").strip() or f"帖子#{aid}"
        sc = a.get("score")
        lines.append(f"{i}. [{title}](/article/{aid}) 相关度={sc}")
    return "\n".join(lines)
