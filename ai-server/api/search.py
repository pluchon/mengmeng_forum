"""
帖子 RAG 语义检索:
  POST /api/v1/article-rag-search
    入参 {"query": "...", "candidates":[{"articleId":1,"text":"..."},...]}
    出参 {"results":[{"articleId":1,"score":0.93}, ...]}

Java 侧 SearchService 在 DB 标题模糊未命中时调用; 这里只做 rerank.
"""
from __future__ import annotations

import logging

from dashscope import TextReRank
from flask import jsonify, request

from api import api
from clients.llm import dashscope_api_key, rerank_model_name
from config import settings

logger = logging.getLogger(__name__)

_RAG = settings.rag


@api.route("/article-rag-search", methods=["POST"])
def article_rag_search():
    data = request.get_json(silent=True) or {}
    query = (data.get("query") or "").strip()
    candidates = data.get("candidates") or []

    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400
    if not isinstance(candidates, list) or not candidates:
        return jsonify({"code": 200, "results": [], "msg": "No candidates"}), 200

    max_candidates = int(_RAG.get("max_candidates", 100))
    doc_trunc = int(_RAG.get("doc_truncate", 800))
    threshold = float(_RAG.get("relevance_threshold", 0.15))
    top_n = int(_RAG.get("top_n", 50))

    seen_ids: set = set()
    docs: list[str] = []
    meta: list = []
    for c in candidates[:max_candidates]:
        if not isinstance(c, dict):
            continue
        aid = c.get("articleId")
        text = (c.get("text") or "").strip()
        if aid is None or not text or aid in seen_ids:
            continue
        seen_ids.add(aid)
        if len(text) > doc_trunc:
            text = text[:doc_trunc]
        docs.append(text)
        meta.append(aid)

    if not docs:
        return jsonify({"code": 200, "results": [], "msg": "Empty candidates after sanitize"}), 200

    try:
        resp = TextReRank.call(
            model=rerank_model_name(),
            query=query,
            documents=docs,
            top_n=min(top_n, len(docs)),
            api_key=dashscope_api_key(),
        )
    except Exception:
        logger.exception("RAG rerank 调用异常")
        return jsonify({"code": 200, "results": [], "msg": "Rerank unavailable"}), 200

    if resp.status_code != 200 or not getattr(resp, "output", None) or not resp.output.results:
        return jsonify({"code": 200, "results": [], "msg": "No rerank result"}), 200

    out: list[dict] = []
    for item in resp.output.results:
        score = float(getattr(item, "relevance_score", 0.0) or 0.0)
        if score < threshold:
            continue
        idx = getattr(item, "index", None)
        if idx is None or idx < 0 or idx >= len(meta):
            continue
        out.append({"articleId": meta[idx], "score": round(score, 4)})
    return jsonify({"code": 200, "results": out, "msg": "success"}), 200


@api.route("/user-rag-search", methods=["POST"])
def user_rag_search():
    """
    用户 RAG 语义检索:
      POST /api/v1/user-rag-search
      入参 {"query": "...", "candidates":[{"userId":1,"text":"..."},...]}
      出参 {"results":[{"userId":1,"score":0.93}, ...]}
    """
    data = request.get_json(silent=True) or {}
    query = (data.get("query") or "").strip()
    candidates = data.get("candidates") or []

    if not query:
        return jsonify({"code": 400, "results": [], "msg": "Missing query"}), 400
    if not isinstance(candidates, list) or not candidates:
        return jsonify({"code": 200, "results": [], "msg": "No candidates"}), 200

    max_candidates = int(_RAG.get("max_candidates", 100))
    doc_trunc = int(_RAG.get("doc_truncate", 800))
    threshold = float(_RAG.get("relevance_threshold", 0.15))
    top_n = int(_RAG.get("top_n", 50))

    seen_ids: set = set()
    docs: list[str] = []
    meta: list = []
    for c in candidates[:max_candidates]:
        if not isinstance(c, dict):
            continue
        uid = c.get("userId")
        text = (c.get("text") or "").strip()
        if uid is None or not text or uid in seen_ids:
            continue
        seen_ids.add(uid)
        if len(text) > doc_trunc:
            text = text[:doc_trunc]
        docs.append(text)
        meta.append(uid)

    if not docs:
        return jsonify({"code": 200, "results": [], "msg": "Empty candidates after sanitize"}), 200

    try:
        resp = TextReRank.call(
            model=rerank_model_name(),
            query=query,
            documents=docs,
            top_n=min(top_n, len(docs)),
            api_key=dashscope_api_key(),
        )
    except Exception:
        logger.exception("用户 RAG rerank 调用异常")
        return jsonify({"code": 200, "results": [], "msg": "Rerank unavailable"}), 200

    if resp.status_code != 200 or not getattr(resp, "output", None) or not resp.output.results:
        return jsonify({"code": 200, "results": [], "msg": "No rerank result"}), 200

    out: list[dict] = []
    for item in resp.output.results:
        score = float(getattr(item, "relevance_score", 0.0) or 0.0)
        if score < threshold:
            continue
        idx = getattr(item, "index", None)
        if idx is None or idx < 0 or idx >= len(meta):
            continue
        out.append({"userId": meta[idx], "score": round(score, 4)})
    return jsonify({"code": 200, "results": out, "msg": "success"}), 200
