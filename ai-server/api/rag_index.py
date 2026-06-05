"""RAG 索引与向量召回 API."""

from __future__ import annotations

import logging

from flask import jsonify, request

from api import api
from clients.dashscope_embedding import embed_query
from config import settings
from rag.indexer import index_published_article
from rag.store import vector_search_articles, vector_search_users
from rag.user_indexer import index_user_profile
from utils.rag_enhance import hybrid_rank

logger = logging.getLogger(__name__)

_RAG = settings.rag


def _internal_auth_ok() -> bool:
    from api.ai_hub import _internal_auth_ok as ok

    return ok()


@api.route("/rag/index-article", methods=["POST"])
def rag_index_article():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403
    data = request.get_json(silent=True) or {}
    try:
        result = index_published_article(data)
    except Exception as e:
        logger.exception("rag index-article 失败")
        return jsonify({"code": 500, "msg": str(e)}), 500
    return jsonify({"code": 200, "msg": "ok", "data": result})


@api.route("/rag/article-vector-search", methods=["POST"])
def rag_article_vector_search():
    """query 向量召回 + 可选 candidates 融合 rerank."""
    data = request.get_json(silent=True) or {}
    query = (data.get("query") or "").strip()
    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400

    qvec = embed_query(query)
    if not qvec:
        return jsonify({"code": 200, "results": [], "msg": "embedding unavailable"}), 200

    vector_hits = vector_search_articles(
        qvec,
        query_text=query,
        top_k=int(_RAG.get("embedding_top_k", 80)),
    )
    if not vector_hits:
        return jsonify({"code": 200, "results": [], "msg": "no index hits"}), 200

    min_vec = float(_RAG.get("vector_min_score", 0.12))
    vector_hits = [h for h in vector_hits if float(h.get("score") or 0) >= min_vec]
    if not vector_hits:
        return jsonify({"code": 200, "results": [], "msg": "below threshold"}), 200

    candidates = data.get("candidates") or []
    if candidates:
        id_to_text = {}
        for c in candidates:
            if isinstance(c, dict) and c.get("articleId") is not None:
                id_to_text[c["articleId"]] = (c.get("text") or "").strip()
        docs, meta = [], []
        for h in vector_hits:
            aid = h["articleId"]
            text = id_to_text.get(aid) or id_to_text.get(str(aid)) or ""
            if not text:
                continue
            docs.append(text[: int(_RAG.get("doc_truncate", 1200))])
            meta.append(aid)
        if docs:
            return jsonify({
                "code": 200,
                "results": hybrid_rank(query, docs, meta, id_key="articleId"),
                "msg": "success",
            }), 200

    return jsonify({"code": 200, "results": vector_hits, "msg": "vector_only"}), 200


@api.route("/rag/index-user", methods=["POST"])
def rag_index_user():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403
    data = request.get_json(silent=True) or {}
    try:
        result = index_user_profile(data)
    except Exception as e:
        logger.exception("rag index-user 失败")
        return jsonify({"code": 500, "msg": str(e)}), 500
    return jsonify({"code": 200, "msg": "ok", "data": result})


@api.route("/rag/user-vector-search", methods=["POST"])
def rag_user_vector_search():
    data = request.get_json(silent=True) or {}
    query = (data.get("query") or "").strip()
    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400

    qvec = embed_query(query)
    if not qvec:
        return jsonify({"code": 200, "results": [], "msg": "embedding unavailable"}), 200

    vector_hits = vector_search_users(
        qvec,
        query_text=query,
        top_k=int(_RAG.get("embedding_top_k", 80)),
    )
    if not vector_hits:
        return jsonify({"code": 200, "results": [], "msg": "no index hits"}), 200

    min_vec = float(_RAG.get("vector_min_score_user", 0.22))
    vector_hits = [h for h in vector_hits if float(h.get("score") or 0) >= min_vec]
    return jsonify({"code": 200, "results": vector_hits, "msg": "vector_only"}), 200
