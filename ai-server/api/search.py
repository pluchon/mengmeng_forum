"""
帖子 / 用户 RAG 语义检索（关键词 + 向量预筛 + rerank 融合）.
"""
from __future__ import annotations

import logging
from typing import Any

from flask import jsonify

from api import api
from api.common import RouteResponse, json_payload
from config import settings
from utils.rag_enhance import hybrid_rank

logger = logging.getLogger(__name__)

_RAG = settings.rag


def _parse_candidates(data: dict[str, Any], *, id_field: str) -> tuple[str, list[str], list[Any]]:
    query_max_len = int(_RAG.get("query_max_len", 500))
    query = str(data.get("query") or "").strip()[:query_max_len]
    candidates = data.get("candidates") or []
    candidates = candidates if isinstance(candidates, list) else []
    max_candidates = int(_RAG.get("max_candidates", 150))
    doc_trunc = int(_RAG.get("doc_truncate", 1200))

    seen_ids: set[Any] = set()
    docs: list[str] = []
    meta: list[Any] = []
    for c in candidates[:max_candidates]:
        if not isinstance(c, dict):
            continue
        cid = c.get(id_field)
        text = (c.get("text") or "").strip()
        if cid is None or not text or cid in seen_ids:
            continue
        seen_ids.add(cid)
        if len(text) > doc_trunc:
            text = text[:doc_trunc]
        docs.append(text)
        meta.append(cid)

    return query, docs, meta


@api.route("/article-rag-search", methods=["POST"])
def article_rag_search() -> RouteResponse:
    data = json_payload()
    query, docs, meta = _parse_candidates(data, id_field="articleId")

    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400
    if not docs:
        return jsonify({"code": 200, "results": [], "msg": "No candidates"}), 200

    results = hybrid_rank(query, docs, meta, id_key="articleId", light=True)
    return jsonify({"code": 200, "results": results, "msg": "success"}), 200


@api.route("/user-rag-search", methods=["POST"])
def user_rag_search() -> RouteResponse:
    data = json_payload()
    query, docs, meta = _parse_candidates(data, id_field="userId")

    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400
    if not docs:
        return jsonify({"code": 200, "results": [], "msg": "No candidates"}), 200

    results = hybrid_rank(query, docs, meta, id_key="userId", light=True)
    return jsonify({"code": 200, "results": results, "msg": "success"}), 200
