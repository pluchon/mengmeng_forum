"""创作能力的稳定模块入口，正式草稿持久化仍由 Java 负责。"""

from __future__ import annotations

import asyncio

from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from modules.creation.service import generate_cover_hints, generate_image, generate_polished_content


class PostPolishModule:
    """帖子正文一键润色模块。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        kind = str(request.payload.get("kind") or "").strip().lower()
        title = str(request.payload.get("title") or "").strip()
        content = str(request.payload.get("content") or "").strip()
        editor_mode = str(request.payload.get("editorMode") or "rich").strip().lower()
        if not kind or not content:
            raise ModuleRequestError("INVALID_POLISH_PAYLOAD", "kind 和 content 为必填字段")
        if editor_mode not in {"rich", "markdown"}:
            raise ModuleRequestError("INVALID_POLISH_PAYLOAD", "editorMode 必须为 rich 或 markdown")
        polished, usage = await asyncio.to_thread(
            generate_polished_content,
            kind,
            title[:200],
            content[:32000],
            editor_mode,
        )
        return ModuleResult(success=True, data={"content": polished, "artifactType": "POST_POLISH"}, usage=usage)


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


class ImageGenerationModule:
    """生图产物模块，不直接写入业务草稿或发布状态。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        prompt = str(request.payload.get("prompt") or "").strip()
        quality = str(request.payload.get("quality") or "normal").strip().lower()
        if not prompt:
            raise ModuleRequestError("INVALID_IMAGE_PAYLOAD", "prompt 不能为空")
        if quality not in {"normal", "premium"}:
            raise ModuleRequestError("INVALID_IMAGE_PAYLOAD", "quality 必须为 normal 或 premium")
        url, usage, mcp_used = await asyncio.to_thread(generate_image, prompt, quality)
        return ModuleResult(
            success=True,
            data={"url": url, "artifactType": "IMAGE", "mcpUsed": mcp_used},
            usage=usage,
        )
