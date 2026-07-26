"""创作能力的稳定模块入口，正式草稿持久化仍由 Java 负责。"""

from __future__ import annotations

import asyncio
from typing import Any

from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from modules.creation.service import generate_cover_hints, generate_image, generate_write_content


class PostWriteModule:
    """正文生成产物模块。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        kind = str(request.payload.get("kind") or "").strip().lower()
        messages = request.payload.get("messages")
        if not kind or not isinstance(messages, list):
            raise ModuleRequestError("INVALID_CREATION_PAYLOAD", "kind 和 messages 为必填字段")
        normalized = _clean_messages(messages)
        if not normalized:
            raise ModuleRequestError("INVALID_CREATION_PAYLOAD", "messages 至少包含一条有效消息")
        content, usage = await asyncio.to_thread(generate_write_content, kind, normalized)
        return ModuleResult(success=True, data={"content": content, "artifactType": "POST_BODY"}, usage=usage)


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


def _clean_messages(raw: list[Any]) -> list[dict[str, str]]:
    messages: list[dict[str, str]] = []
    for item in raw[-24:]:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role") or "").strip().lower()
        content = str(item.get("content") or "").strip()
        if role in {"system", "user", "assistant"} and content:
            messages.append({"role": role, "content": content[:32000]})
    return messages
