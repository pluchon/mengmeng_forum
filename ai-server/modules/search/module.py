"""RAG 检索模块；只返回候选 ID 和分数，Java 负责最终权限过滤。"""

from __future__ import annotations

import asyncio
from typing import Any

from clients.dashscope_embedding import embedding_similarities
from rag.search_service import clean_query, search_articles_by_vector, search_emojis_by_vector, search_users_by_vector
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils.rag_enhance import hybrid_rank


class SearchModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        scope = str(request.payload.get("scope") or request.intent).strip().upper()
        query = clean_query(request.payload.get("query"))
        if not query:
            raise ModuleRequestError("INVALID_SEARCH_PAYLOAD", "query 不能为空")
        if scope not in {"ARTICLE", "EMOJI", "USER", "MIXED", "CANDIDATE"}:
            raise ModuleRequestError("INVALID_SEARCH_PAYLOAD", "scope 必须为 ARTICLE、EMOJI、USER、MIXED 或 CANDIDATE")
        candidates = request.payload.get("candidates")
        candidate_list = candidates if isinstance(candidates, list) else []
        article_results = await self._articles(query, candidate_list) if scope in {"ARTICLE", "MIXED"} else []
        emoji_results = await self._emojis(query) if scope == "EMOJI" else []
        user_results = await self._users(query, candidate_list) if scope in {"USER", "MIXED"} else []
        candidate_results = await self._candidates(query, candidate_list) if scope == "CANDIDATE" else []
        return ModuleResult(
            success=True,
            data={
                "scope": scope,
                "query": query,
                "articleResults": article_results,
                "emojiResults": emoji_results,
                "userResults": user_results,
                "candidateResults": candidate_results,
                "requiresJavaPermissionFilter": True,
            },
        )

    @staticmethod
    async def _articles(query: str, candidates: list[Any]) -> list[dict[str, Any]]:
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
