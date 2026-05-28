"""
帖子 / 用户 RAG 语义检索（关键词 + 向量预筛 + rerank 融合）.
"""
from __future__ import annotations

import logging

from flask import jsonify, request

from api import api
from config import settings
from utils.rag_enhance import hybrid_rank

logger = logging.getLogger(__name__)

_RAG = settings.rag


def _parse_candidates(data: dict, *, id_field: str) -> tuple[str, list[str], list]:
    query = (data.get("query") or "").strip()
    candidates = data.get("candidates") or []
    max_candidates = int(_RAG.get("max_candidates", 150))
    doc_trunc = int(_RAG.get("doc_truncate", 1200))

    seen_ids: set = set()
    docs: list[str] = []
    meta: list = []
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
def article_rag_search():
    data = request.get_json(silent=True) or {}
    query, docs, meta = _parse_candidates(data, id_field="articleId")

    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400
    if not docs:
        return jsonify({"code": 200, "results": [], "msg": "No candidates"}), 200

    results = hybrid_rank(query, docs, meta, id_key="articleId")
    return jsonify({"code": 200, "results": results, "msg": "success"}), 200


@api.route("/user-rag-search", methods=["POST"])
def user_rag_search():
    data = request.get_json(silent=True) or {}
    query, docs, meta = _parse_candidates(data, id_field="userId")

    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400
    if not docs:
        return jsonify({"code": 200, "results": [], "msg": "No candidates"}), 200

    results = hybrid_rank(query, docs, meta, id_key="userId")
    return jsonify({"code": 200, "results": results, "msg": "success"}), 200
