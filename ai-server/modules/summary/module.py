"""复用现有模型能力的帖子摘要模块。"""

from __future__ import annotations

import asyncio

from clients.llm import text_llm
from config import settings
from graphs.prompts import SUMMARY_TEMPLATE
from runtime.ai_runtime import AiRuntime
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils.html import clean_html

_runtime = AiRuntime()


class PostSummaryModule:
    """短帖直接总结，长帖先分块再汇总。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        content = request.payload.get("content")
        if not isinstance(content, str):
            raise ModuleRequestError("INVALID_SUMMARY_PAYLOAD", "content 必须是文本")
        plain = clean_html(content).strip()
        if not plain:
            raise ModuleRequestError("INVALID_SUMMARY_PAYLOAD", "content 不能为空")
        max_chars = int(settings.audit.get("text_audit_max_chars", 12000))
        chunks = [plain[index:index + max_chars] for index in range(0, len(plain), max_chars)]
        partials = [await self._summarize(chunk, request.trace_id) for chunk in chunks]
        summary = partials[0] if len(partials) == 1 else await self._summarize("\n".join(partials), request.trace_id)
        return ModuleResult(
            success=True,
            data={
                "summary": summary,
                "highlights": [summary] if summary else [],
                "keywords": [],
                "riskLevel": "UNKNOWN",
                "qualityScore": 1.0 if summary else 0.0,
                "chunkCount": len(chunks),
            },
        )

    @staticmethod
    async def _summarize(content: str, trace_id: str) -> str:
        def invoke() -> str:
            response = _runtime.call_llm(
                lambda: (SUMMARY_TEMPLATE | text_llm(temperature=0.3)).invoke({"text": content}),
                trace_id=trace_id,
                model_name=str(settings.dashscope.get("model_text") or "qwen3.6-flash"),
            )
            raw = getattr(response, "content", response)
            if isinstance(raw, list) and raw:
                first = raw[0]
                return str(first.get("text", "") if isinstance(first, dict) else first).strip()
            return str(raw or "").strip()

        return await asyncio.to_thread(invoke)
