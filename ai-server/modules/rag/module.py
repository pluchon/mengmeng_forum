"""将索引写入和删除收敛到 AI Gateway。"""

from __future__ import annotations

import asyncio
from typing import Any

from rag.emoji_indexer import index_emoji_shop
from rag.indexer import index_published_article
from rag.music_indexer import index_music_track
from rag.store import remove_article_index
from rag.user_indexer import index_user_profile
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult


class RagIndexArticleModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        result = await asyncio.to_thread(index_published_article, request.payload)
        return ModuleResult(success=True, data=_as_data(result))


class RagIndexUserModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        result = await asyncio.to_thread(index_user_profile, request.payload)
        return ModuleResult(success=True, data=_as_data(result))


class RagIndexEmojiModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        result = await asyncio.to_thread(index_emoji_shop, request.payload)
        return ModuleResult(success=True, data=_as_data(result))


class RagIndexMusicModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        result = await asyncio.to_thread(index_music_track, request.payload)
        return ModuleResult(success=True, data=_as_data(result))


class RagRemoveArticleModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        article_id = request.payload.get("articleId", request.payload.get("article_id"))
        try:
            normalized_article_id = int(article_id)
        except (TypeError, ValueError) as exc:
            raise ModuleRequestError("INVALID_RAG_PAYLOAD", "articleId 必须是正整数") from exc
        if normalized_article_id <= 0:
            raise ModuleRequestError("INVALID_RAG_PAYLOAD", "articleId 必须是正整数")
        await asyncio.to_thread(remove_article_index, normalized_article_id)
        return ModuleResult(success=True, data={"articleId": normalized_article_id, "removed": True})


def _as_data(raw: Any) -> dict[str, Any]:
    return raw if isinstance(raw, dict) else {"result": raw}
