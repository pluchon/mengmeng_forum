"""RAG 索引与向量召回 API."""

from __future__ import annotations

import logging
from typing import Any

from flask import Response, jsonify, request

from api import api
from rag.indexer import index_published_article
from rag.search_service import clean_query, search_articles_by_vector, search_users_by_vector
from rag.user_indexer import index_user_profile

logger = logging.getLogger(__name__)


def _internal_auth_ok() -> bool:
    from api.ai_hub import _internal_auth_ok as ok

    return ok()


def _internal_auth_error() -> tuple[Response, int] | None:
    if _internal_auth_ok():
        return None
    return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403


def _json_payload() -> dict[str, Any]:
    data = request.get_json(silent=True) or {}
    return data if isinstance(data, dict) else {}


@api.route("/rag/index-article", methods=["POST"])
def rag_index_article():
    auth_error = _internal_auth_error()
    if auth_error:
        return auth_error
    data = _json_payload()
    try:
        result = index_published_article(data)
    except Exception:
        logger.exception("rag index-article 失败")
        return jsonify({"code": 500, "msg": "rag index failed"}), 500
    return jsonify({"code": 200, "msg": "ok", "data": result})


@api.route("/rag/article-vector-search", methods=["POST"])
def rag_article_vector_search():
    """query 向量召回 + 可选 candidates 融合 rerank."""
    auth_error = _internal_auth_error()
    if auth_error:
        return auth_error
    data = _json_payload()
    query = clean_query(data.get("query"))
    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400

    candidates = data.get("candidates")
    candidates = candidates if isinstance(candidates, list) else []
    try:
        results, msg = search_articles_by_vector(query, candidates)
    except Exception:
        logger.exception("rag article-vector-search 失败")
        return jsonify({"code": 500, "results": [], "msg": "rag vector search failed"}), 500
    return jsonify({"code": 200, "results": results, "msg": msg}), 200


@api.route("/rag/index-user", methods=["POST"])
def rag_index_user():
    auth_error = _internal_auth_error()
    if auth_error:
        return auth_error
    data = _json_payload()
    try:
        result = index_user_profile(data)
    except Exception:
        logger.exception("rag index-user 失败")
        return jsonify({"code": 500, "msg": "rag user index failed"}), 500
    return jsonify({"code": 200, "msg": "ok", "data": result})


@api.route("/rag/user-vector-search", methods=["POST"])
def rag_user_vector_search():
    auth_error = _internal_auth_error()
    if auth_error:
        return auth_error
    data = _json_payload()
    query = clean_query(data.get("query"))
    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400

    try:
        results, msg = search_users_by_vector(query)
    except Exception:
        logger.exception("rag user-vector-search 失败")
        return jsonify({"code": 500, "results": [], "msg": "rag vector search failed"}), 500
    return jsonify({"code": 200, "results": results, "msg": msg}), 200
