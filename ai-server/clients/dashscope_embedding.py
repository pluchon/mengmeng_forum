"""Dashscope 向量（RAG：qwen3-vl-embedding 多模态 + text-embedding-v3 文本降级）."""

from __future__ import annotations

import logging
import math
from typing import Any, Sequence

import requests
from dashscope import MultiModalEmbedding, TextEmbedding

from clients.dashscope_chat_client import dashscope_compat_base
from clients.llm import dashscope_api_key, embedding_model_name, embedding_text_fallback_model_name
logger = logging.getLogger(__name__)


def _field(obj: Any, key: str, default: Any = None) -> Any:
    """Dashscope SDK 的 output / item 常为普通 dict，不能用 getattr 取键."""
    if obj is None:
        return default
    if isinstance(obj, dict):
        return obj.get(key, default)
    return getattr(obj, key, default)


def _cosine(a: Sequence[float], b: Sequence[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na <= 0 or nb <= 0:
        return 0.0
    return dot / (na * nb)


def _vec_from_item(item: Any) -> list[float] | None:
    vec = _field(item, "embedding")
    if vec is None:
        return None
    if isinstance(vec, list):
        return [float(x) for x in vec] if vec else None
    try:
        return [float(x) for x in list(vec)]
    except TypeError:
        return None


def _embeddings_from_output(output: Any) -> list[Any]:
    emb = _field(output, "embeddings")
    if isinstance(emb, list) and emb:
        return emb
    # 旧版/融合向量偶发顶层 embedding
    single = _field(output, "embedding")
    if isinstance(single, list) and single:
        return [{"embedding": single, "index": 0}]
    return []


def _extract_embedding_vec(resp) -> list[float] | None:
    if resp.status_code != 200 or not _field(resp, "output"):
        code = _field(resp, "code")
        msg = _field(resp, "message") or str(resp)
        logger.warning(
            "Dashscope embedding 无结果 status=%s code=%s msg=%s",
            resp.status_code,
            code,
            msg,
        )
        return None
    emb = _embeddings_from_output(_field(resp, "output"))
    if not emb:
        logger.warning(
            "Dashscope embedding output 无向量 keys=%s code=%s msg=%s",
            list(_field(resp, "output").keys()) if isinstance(_field(resp, "output"), dict) else "?",
            _field(resp, "code"),
            _field(resp, "message"),
        )
        return None
    return _vec_from_item(emb[0])


def _embed_multimodal(inputs: list[dict]) -> list[float] | None:
    """qwen3-vl-embedding：MultiModalEmbedding（与对话用的 OpenAI 兼容接口不同）."""
    key = dashscope_api_key()
    if not key or not inputs:
        return None
    model = embedding_model_name()
    kwargs: dict = {"model": model, "input": inputs, "api_key": key}
    if len(inputs) > 1:
        kwargs["enable_fusion"] = True
    try:
        resp = MultiModalEmbedding.call(**kwargs)
    except Exception:
        logger.exception("MultiModalEmbedding 调用异常 model=%s inputs=%s", model, len(inputs))
        return None
    return _extract_embedding_vec(resp)


def _embed_openai_compat(texts: list[str], *, model: str) -> list[list[float]] | None:
    """与看板娘相同的 compatible-mode /embeddings，SDK 解析失败时的兜底."""
    key = dashscope_api_key()
    if not key or not texts:
        return None
    url = f"{dashscope_compat_base()}/embeddings"
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    out: list[list[float]] = []
    for text in texts:
        try:
            r = requests.post(
                url,
                headers=headers,
                json={"model": model, "input": text},
                timeout=60,
            )
        except Exception:
            logger.exception("OpenAI 兼容 embeddings 请求异常 model=%s", model)
            return None
        if not r.ok:
            logger.warning("OpenAI 兼容 embeddings HTTP %s: %s", r.status_code, r.text[:300])
            return None
        data = r.json()
        rows = data.get("data") or []
        if not rows:
            logger.warning("OpenAI 兼容 embeddings 无 data: %s", str(data)[:300])
            return None
        vec = rows[0].get("embedding")
        if not isinstance(vec, list) or not vec:
            return None
        out.append([float(x) for x in vec])
    return out if len(out) == len(texts) else None


def embed_rag_document(*, doc_text: str, video_or_image_url: str = "", media_type: int = 0) -> list[float] | None:
    """帖子入库向量：优先标题文本；封面/视频 URL 可选增强（失败不阻断）."""
    doc_text = (doc_text or "").strip()[:2048]
    if not doc_text:
        return None

    vec = _embed_multimodal([{"text": doc_text}])
    if vec:
        return vec

    vecs = embed_texts([doc_text])
    if vecs:
        return vecs[0]

    url = (video_or_image_url or "").strip()
    if not url.startswith(("http://", "https://")):
        return None
    if media_type == 1:
        if url.lower().endswith((".mp4", ".mov", ".webm", ".mkv")) or "video" in url.lower():
            inputs = [{"text": doc_text}, {"video": url}]
        else:
            inputs = [{"text": doc_text}, {"image": url}]
    else:
        inputs = [{"text": doc_text}, {"image": url}]
    vec = _embed_multimodal(inputs)
    if vec:
        return vec
    vecs = embed_texts([doc_text])
    return vecs[0] if vecs else None


def embed_query(query: str) -> list[float] | None:
    """与入库 embed_rag_document 同一路径，避免多模态/文本向量维度不一致导致相似度恒为 0."""
    from rag.keyword_expand import expand_search_query

    q = expand_search_query(query)[:512]
    if not q:
        return None
    return embed_rag_document(doc_text=q, video_or_image_url="", media_type=0)


def embed_texts(texts: list[str]) -> list[list[float]] | None:
    """TextEmbedding 仅支持 text-embedding-v3/v4 等文本模型."""
    key = dashscope_api_key()
    if not key:
        logger.warning("TextEmbedding 跳过：DASHSCOPE_API_KEY 未配置")
        return None
    clean = [(t or "").strip()[:2048] for t in texts]
    if not clean or not any(clean):
        return None
    model = embedding_text_fallback_model_name()
    try:
        resp = TextEmbedding.call(model=model, input=clean, api_key=key)
    except Exception:
        logger.exception("TextEmbedding 调用异常 model=%s", model)
        return _embed_openai_compat(clean, model=model)

    if resp.status_code != 200 or not _field(resp, "output"):
        code = _field(resp, "code")
        msg = _field(resp, "message") or str(resp)
        logger.warning(
            "TextEmbedding 无结果 status=%s code=%s msg=%s model=%s",
            resp.status_code,
            code,
            msg,
            model,
        )
        return _embed_openai_compat(clean, model=model)

    embeddings = _embeddings_from_output(_field(resp, "output"))
    out: list[list[float]] = []
    for item in embeddings:
        vec = _vec_from_item(item)
        if vec:
            out.append(vec)
    if len(out) == len(clean):
        return out

    logger.warning(
        "TextEmbedding 数量不匹配 expect=%s got=%s model=%s，尝试 compatible-mode",
        len(clean),
        len(out),
        model,
    )
    return _embed_openai_compat(clean, model=model)


def embedding_similarities(query: str, documents: list[str]) -> list[float] | None:
    if not query.strip() or not documents:
        return None
    vecs = embed_texts([query, *documents])
    if not vecs or len(vecs) < 2:
        return None
    qv = vecs[0]
    return [_cosine(qv, dv) for dv in vecs[1:]]
