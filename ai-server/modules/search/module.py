"""RAG 检索模块；只返回候选 ID 和分数，Java 负责最终权限过滤。"""

from __future__ import annotations

import asyncio
from typing import Any

from clients.dashscope_embedding import embedding_similarities
from config import settings
from rag.search_service import (
    clean_query,
    search_articles_by_vector,
    search_emojis_by_vector,
    search_musics_by_vector,
    search_users_by_vector,
)
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils.rag_enhance import hybrid_rank

# 字段权重按“实际可用字段”重新归一化（缺 summary/author 时不稀释总分），
# 所以三者是一组，必须整组同时可配，不能各自独立取默认值。
_RAG = settings.rag
_FIELD_WEIGHT_TITLE = float(_RAG.get("field_weight_title", 0.60))
_FIELD_WEIGHT_SUMMARY = float(_RAG.get("field_weight_summary", 0.30))
_FIELD_WEIGHT_AUTHOR = float(_RAG.get("field_weight_author", 0.10))
_FIELD_MIN_SCORE = float(_RAG.get("field_min_score", 0.22))


class SearchModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        scope = str(request.payload.get("scope") or request.intent).strip().upper()
        query = clean_query(request.payload.get("query"))
        if not query:
            raise ModuleRequestError("INVALID_SEARCH_PAYLOAD", "query 不能为空")
        if scope not in {"ARTICLE", "EMOJI", "USER", "MUSIC", "MIXED", "CANDIDATE"}:
            raise ModuleRequestError("INVALID_SEARCH_PAYLOAD", "scope 必须为 ARTICLE、EMOJI、USER、MUSIC、MIXED 或 CANDIDATE")
        candidates = request.payload.get("candidates")
        candidate_list = candidates if isinstance(candidates, list) else []
        article_results = await self._articles(query, candidate_list) if scope in {"ARTICLE", "MIXED"} else []
        emoji_results = await self._emojis(query) if scope == "EMOJI" else []
        user_results = await self._users(query, candidate_list) if scope in {"USER", "MIXED"} else []
        music_results = await self._musics(query) if scope == "MUSIC" else []
        candidate_results = await self._candidates(query, candidate_list) if scope == "CANDIDATE" else []
        return ModuleResult(
            success=True,
            data={
                "scope": scope,
                "query": query,
                "articleResults": article_results,
                "emojiResults": emoji_results,
                "userResults": user_results,
                "musicResults": music_results,
                "candidateResults": candidate_results,
                "requiresJavaPermissionFilter": True,
            },
        )

    @staticmethod
    async def _articles(query: str, candidates: list[Any]) -> list[dict[str, Any]]:
        field_results = await _rank_article_fields(query, candidates)
        if field_results:
            return field_results
        results, _ = await asyncio.to_thread(search_articles_by_vector, query, candidates)
        if results:
            return results
        return await asyncio.to_thread(_keyword_rank, query, candidates, "articleId")

    @staticmethod
    async def _users(query: str, candidates: list[Any]) -> list[dict[str, Any]]:
        results, _ = await asyncio.to_thread(search_users_by_vector, query)
        if results:
            return results
        return await asyncio.to_thread(_keyword_rank, query, candidates, "userId")

    @staticmethod
    async def _emojis(query: str) -> list[dict[str, Any]]:
        results, _ = await asyncio.to_thread(search_emojis_by_vector, query)
        return results

    @staticmethod
    async def _musics(query: str) -> list[dict[str, Any]]:
        results, _ = await asyncio.to_thread(search_musics_by_vector, query)
        return results

    @staticmethod
    async def _candidates(query: str, candidates: list[Any]) -> list[dict[str, Any]]:
        candidate_ids: list[Any] = []
        documents: list[str] = []
        for item in candidates[:120]:
            if not isinstance(item, dict):
                continue
            candidate_id = item.get("candidateId")
            text = str(item.get("text") or "").strip()
            if candidate_id is None or not text:
                continue
            candidate_ids.append(candidate_id)
            documents.append(text[:1200])
        if not documents:
            return []
        scores = await asyncio.to_thread(embedding_similarities, query, documents)
        if not scores:
            return await asyncio.to_thread(_keyword_rank, query, candidates, "candidateId")
        ranked = [
            {"candidateId": candidate_id, "score": round(float(score), 6)}
            for candidate_id, score in zip(candidate_ids, scores)
            if float(score) >= 0.18
        ]
        ranked.sort(key=lambda item: item["score"], reverse=True)
        return ranked


def _keyword_rank(query: str, candidates: list[Any], id_key: str) -> list[dict[str, Any]]:
    docs: list[str] = []
    ids: list[Any] = []
    for item in candidates[:150]:
        if not isinstance(item, dict):
            continue
        identity = item.get(id_key)
        text = str(item.get("text") or "").strip()
        if identity is None or not text:
            continue
        ids.append(identity)
        docs.append(text[:1200])
    return hybrid_rank(query, docs, ids, id_key=id_key, light=True)


async def _rank_article_fields(query: str, candidates: list[Any]) -> list[dict[str, Any]]:
    """分别计算标题、公共总结和作者昵称相似度，再按可用字段重新归一化。"""
    valid: list[dict[str, Any]] = []
    for item in candidates[:150]:
        if not isinstance(item, dict) or item.get("articleId") is None:
            continue
        title = str(item.get("title") or "").strip()
        summary = str(item.get("summary") or "").strip()
        author = str(item.get("authorNickname") or "").strip()
        if not title and not summary and not author:
            continue
        valid.append({
            "articleId": item.get("articleId"),
            "title": title[:300],
            "summary": summary[:800],
            "author": author[:80],
        })
    if not valid:
        return []

    async def similarities(field: str) -> list[float]:
        documents = [row[field] for row in valid]
        return await asyncio.to_thread(embedding_similarities, query, documents)

    title_scores, summary_scores, author_scores = await asyncio.gather(
        similarities("title"), similarities("summary"), similarities("author")
    )
    if not title_scores:
        return []

    ranked: list[dict[str, Any]] = []
    normalized_query = query.casefold()
    for index, row in enumerate(valid):
        weighted = 0.0
        available_weight = 0.0
        for field, weight, scores in (
            ("title", _FIELD_WEIGHT_TITLE, title_scores),
            ("summary", _FIELD_WEIGHT_SUMMARY, summary_scores),
            ("author", _FIELD_WEIGHT_AUTHOR, author_scores),
        ):
            if not row[field] or index >= len(scores):
                continue
            weighted += weight * max(0.0, float(scores[index]))
            available_weight += weight
        if available_weight <= 0:
            continue
        score = weighted / available_weight
        title_exact = row["title"].casefold() == normalized_query
        if score >= _FIELD_MIN_SCORE or title_exact:
            ranked.append({
                "articleId": row["articleId"],
                "score": round(score, 6),
                "titleExact": title_exact,
            })
    ranked.sort(key=lambda item: (bool(item["titleExact"]), float(item["score"])), reverse=True)
    return ranked
