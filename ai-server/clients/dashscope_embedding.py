"""Dashscope 文本向量（RAG 预筛）."""

from __future__ import annotations

import logging
import math
from typing import Sequence

from dashscope import TextEmbedding

from clients.llm import dashscope_api_key, embedding_model_name

logger = logging.getLogger(__name__)


def _cosine(a: Sequence[float], b: Sequence[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na <= 0 or nb <= 0:
        return 0.0
    return dot / (na * nb)


def embed_texts(texts: list[str]) -> list[list[float]] | None:
    """批量 embedding；失败时返回 None（调用方降级为仅 rerank）."""
    key = dashscope_api_key()
    if not key:
        return None
    clean = [(t or "").strip()[:2048] for t in texts]
    if not clean or not any(clean):
        return None
    model = embedding_model_name()
    try:
        resp = TextEmbedding.call(model=model, input=clean, api_key=key)
    except Exception:
        logger.exception("Dashscope embedding 调用失败")
        return None
    if resp.status_code != 200 or not getattr(resp, "output", None):
        logger.warning("Dashscope embedding 无结果: %s", getattr(resp, "message", resp))
        return None
    embeddings = getattr(resp.output, "embeddings", None) or []
    out: list[list[float]] = []
    for item in embeddings:
        vec = getattr(item, "embedding", None)
        if vec is None and isinstance(item, dict):
            vec = item.get("embedding")
        if isinstance(vec, list):
            out.append([float(x) for x in vec])
    if len(out) != len(clean):
        logger.warning("embedding 数量不匹配 expect=%s got=%s", len(clean), len(out))
        return None
    return out


def embedding_similarities(query: str, documents: list[str]) -> list[float] | None:
    """query 与 documents 的余弦相似度列表；失败返回 None."""
    if not query.strip() or not documents:
        return None
    vecs = embed_texts([query, *documents])
    if not vecs or len(vecs) < 2:
        return None
    qv = vecs[0]
    return [_cosine(qv, dv) for dv in vecs[1:]]
