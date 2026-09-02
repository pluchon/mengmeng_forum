"""创作能力的稳定模块入口，正式草稿持久化仍由 Java 负责。"""

from __future__ import annotations

import asyncio

from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from modules.creation.cover_graph import run_cover_graph
from modules.creation.polish_graph import run_polish_graph
from modules.creation.service import generate_cover_hints, generate_image
from modules.creation.music_graph import run_music_recommend_graph, run_music_search_graph
from modules.creation.tag_graph import find_high_similarity, run_tag_recommend_graph


class PostPolishModule:
    """帖子正文一键润色模块。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        title = str(request.payload.get("title") or "").strip()
        content = str(request.payload.get("content") or "").strip()
        editor_mode = str(request.payload.get("editorMode") or "rich").strip().lower()
        if not content:
            raise ModuleRequestError("INVALID_POLISH_PAYLOAD", "content 为必填字段")
        if editor_mode not in {"rich", "markdown"}:
            raise ModuleRequestError("INVALID_POLISH_PAYLOAD", "editorMode 必须为 rich 或 markdown")
        # 档位由 Java 按登录态填入。深度模型只对 PRO/MAX 开放，
        # 这个限制必须在代码里卡死——提示词里写「你是免费用户别用深度模型」拦不住，
        # 因为选模型的是下面的代码，不是模型自己。
        vip_tier = 0
        try:
            vip_tier = int(request.payload.get("vipTier") or 0)
        except (TypeError, ValueError):
            vip_tier = 0
        result = await asyncio.to_thread(
            run_polish_graph,
            title[:200],
            content[:32000],
            editor_mode,
            vip_tier >= 1,
        )
        return ModuleResult(
            success=True,
            data={
                "content": result["content"],
                "artifactType": "POST_POLISH",
                "route": result["route"],
                "candidateCount": result["candidateCount"],
                "deepUsed": result["deepUsed"],
            },
            usage=result["usage"],
        )


class CoverHintsModule:
    """封面提示词生成模块。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        article = str(request.payload.get("articleText") or request.payload.get("article_text") or "").strip()
        if not article:
            raise ModuleRequestError("INVALID_COVER_PAYLOAD", "articleText 不能为空")
        content, usage = await asyncio.to_thread(generate_cover_hints, article[:12000])
        return ModuleResult(
            success=True,
            data={"content": content, "prompt": content, "artifactType": "COVER_PROMPT"},
            usage=usage,
        )


class ArticleCoverModule:
    """文章理解、按需检索、提示词与生图的一键封面子图。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        title = str(request.payload.get("title") or "").strip()
        content = str(request.payload.get("content") or "").strip()
        editor_mode = str(request.payload.get("editorMode") or "rich").strip().lower()
        user_prompt = str(request.payload.get("userPrompt") or "").strip()
        quality = str(request.payload.get("quality") or "normal").strip().lower()
        if not content:
            raise ModuleRequestError("INVALID_COVER_PAYLOAD", "content 不能为空")
        if editor_mode not in {"rich", "markdown"}:
            raise ModuleRequestError("INVALID_COVER_PAYLOAD", "editorMode 必须为 rich 或 markdown")
        if quality != "normal":
            raise ModuleRequestError("INVALID_COVER_PAYLOAD", "quality 仅支持 normal")
        result = await asyncio.to_thread(
            run_cover_graph,
            title[:200],
            content[:32000],
            editor_mode,
            user_prompt[:200],
            quality,
        )
        return ModuleResult(
            success=True,
            data={
                "url": result["url"],
                "prompt": result["prompt"],
                "model": result["model"],
                "mcpUsed": result["mcpUsed"],
                "artifactType": "ARTICLE_COVER",
            },
            usage=result["usage"],
        )


class ArticleTagRecommendModule:
    """基于共享文章理解结果选择已有帖子标签。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        title = str(request.payload.get("title") or "").strip()
        content = str(request.payload.get("content") or "").strip()
        editor_mode = str(request.payload.get("editorMode") or "rich").strip().lower()
        candidates = request.payload.get("candidates")
        if not title and not content:
            raise ModuleRequestError("INVALID_TAG_RECOMMEND_PAYLOAD", "标题和正文不能同时为空")
        if editor_mode not in {"rich", "markdown"}:
            raise ModuleRequestError("INVALID_TAG_RECOMMEND_PAYLOAD", "editorMode 必须为 rich 或 markdown")
        if not isinstance(candidates, list):
            raise ModuleRequestError("INVALID_TAG_RECOMMEND_PAYLOAD", "candidates 必须为数组")
        result = await asyncio.to_thread(
            run_tag_recommend_graph,
            title[:200],
            content[:32000],
            editor_mode,
            candidates[:200],
        )
        return ModuleResult(
            success=True,
            data={
                "tagIds": result["tagIds"],
                "summary": result["summary"],
                "deepUsed": result["deepUsed"],
                "artifactType": "ARTICLE_TAG_RECOMMENDATION",
            },
            usage=result["usage"],
        )


class ArticleTagSimilarityModule:
    """严格确认待新增标签是否与已有标签几乎同义。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        proposed_name = str(request.payload.get("proposedName") or "").strip()
        candidates = request.payload.get("candidates")
        if not proposed_name:
            raise ModuleRequestError("INVALID_TAG_SIMILARITY_PAYLOAD", "proposedName 不能为空")
        if not isinstance(candidates, list):
            raise ModuleRequestError("INVALID_TAG_SIMILARITY_PAYLOAD", "candidates 必须为数组")
        result = await asyncio.to_thread(find_high_similarity, proposed_name[:12], candidates[:20])
        return ModuleResult(
            success=True,
            data={
                "similarTagId": result["similarTagId"],
                "reason": result["reason"],
                "artifactType": "ARTICLE_TAG_SIMILARITY",
            },
            usage=result["usage"],
        )


class MusicRecommendModule:
    """基于帖子草稿从候选曲库推荐配乐。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        title = str(request.payload.get("title") or "").strip()
        content = str(request.payload.get("content") or "").strip()
        editor_mode = str(request.payload.get("editorMode") or "rich").strip().lower()
        candidates = request.payload.get("candidates")
        mode = str(request.payload.get("mode") or "recommend").strip().lower()
        if not title and not content:
            raise ModuleRequestError("INVALID_MUSIC_RECOMMEND_PAYLOAD", "标题和正文不能同时为空")
        if editor_mode not in {"rich", "markdown"}:
            raise ModuleRequestError("INVALID_MUSIC_RECOMMEND_PAYLOAD", "editorMode 必须为 rich 或 markdown")
        if not isinstance(candidates, list):
            raise ModuleRequestError("INVALID_MUSIC_RECOMMEND_PAYLOAD", "candidates 必须为数组")
        if mode not in {"recommend", "prefilter"}:
            raise ModuleRequestError("INVALID_MUSIC_RECOMMEND_PAYLOAD", "mode 必须为 recommend 或 prefilter")
        result = await asyncio.to_thread(
            run_music_recommend_graph,
            title[:200],
            content[:32000],
            editor_mode,
            candidates[:200],
            mode,
        )
        return ModuleResult(
            success=True,
            data={
                "musicKeys": result["musicKeys"],
                "rationale": result["rationale"],
                "moods": result["moods"],
                "artifactType": "MUSIC_RECOMMENDATION",
            },
            usage=result["usage"],
        )


class MusicSearchModule:
    """基于自然语言 query 从候选曲库 AI 搜索选曲。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        query = str(request.payload.get("query") or "").strip()
        scope = str(request.payload.get("scope") or "all").strip().lower() or "all"
        candidates = request.payload.get("candidates")
        if not query:
            raise ModuleRequestError("INVALID_MUSIC_SEARCH_PAYLOAD", "query 不能为空")
        if not isinstance(candidates, list):
            raise ModuleRequestError("INVALID_MUSIC_SEARCH_PAYLOAD", "candidates 必须为数组")
        result = await asyncio.to_thread(
            run_music_search_graph, query[:240], candidates[:200], scope)
        return ModuleResult(
            success=True,
            data={
                "musicKeys": result["musicKeys"],
                "rationale": result["rationale"],
                "moods": result["moods"],
                "artifactType": "MUSIC_AI_SEARCH",
            },
            usage=result["usage"],
        )


class ImageGenerationModule:
    """生图产物模块，不直接写入业务草稿或发布状态。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        prompt = str(request.payload.get("prompt") or "").strip()
        quality = str(request.payload.get("quality") or "normal").strip().lower()
        if not prompt:
            raise ModuleRequestError("INVALID_IMAGE_PAYLOAD", "prompt 不能为空")
        if quality not in ("normal", "premium"):
            raise ModuleRequestError("INVALID_IMAGE_PAYLOAD", "quality 只能是 normal 或 premium")
        url, usage, mcp_used = await asyncio.to_thread(generate_image, prompt, quality)
        return ModuleResult(
            success=True,
            data={"url": url, "artifactType": "IMAGE", "mcpUsed": mcp_used},
            usage=usage,
        )
