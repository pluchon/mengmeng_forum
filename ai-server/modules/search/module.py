"""RAG 检索模块；只返回候选 ID 和分数，Java 负责最终权限过滤。"""

from __future__ import annotations

import asyncio
from typing import Any

from rag.search_service import clean_query, search_articles_by_vector, search_users_by_vector
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils.rag_enhance import hybrid_rank


class SearchModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        scope = str(request.payload.get("scope") or request.intent).strip().upper()
        query = clean_query(request.payload.get("query"))
        if not query:
            raise ModuleRequestError("INVALID_SEARCH_PAYLOAD", "query 不能为空")
        if scope not in {"ARTICLE", "USER", "MIXED"}:
            raise ModuleRequestError("INVALID_SEARCH_PAYLOAD", "scope 必须为 ARTICLE、USER 或 MIXED")
        candidates = request.payload.get("candidates")
        candidate_list = candidates if isinstance(candidates, list) else []
        article_results = await self._articles(query, candidate_list) if scope in {"ARTICLE", "MIXED"} else []
        user_results = await self._users(query, candidate_list) if scope in {"USER", "MIXED"} else []
        return ModuleResult(
            success=True,
            data={
                "scope": scope,
                "query": query,
                "articleResults": article_results,
                "userResults": user_results,
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
