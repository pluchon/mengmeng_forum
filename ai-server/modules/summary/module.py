"""帖子详情页自适应总结模块。"""

from __future__ import annotations

from modules.summary.graph import run_summary_graph
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils.html import clean_html


class PostSummaryModule:
    """按语义复杂度路由1至3个worker并评估择优。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        content = request.payload.get("content")
        if not isinstance(content, str):
            raise ModuleRequestError("INVALID_SUMMARY_PAYLOAD", "content 必须是文本")
        plain = clean_html(content).strip()
        if not plain:
            raise ModuleRequestError("INVALID_SUMMARY_PAYLOAD", "content 不能为空")
        if len(plain) <= 50:
            raise ModuleRequestError("SUMMARY_CONTENT_TOO_SHORT", "帖子正文不超过50字")
        result = run_summary_graph(str(request.payload.get("title") or ""), plain)
        summary = str(result.get("summary") or "")
        return ModuleResult(
            success=True,
            data={
                "summary": summary,
                "highlights": [summary] if summary else [],
                "keywords": [],
                "riskLevel": "UNKNOWN",
                "qualityScore": 1.0 if summary else 0.0,
                "route": result.get("route"),
                "candidateCount": result.get("candidateCount", 0),
                "deepUsed": result.get("deepUsed", False),
                "mcpUsed": result.get("mcpUsed", False),
            },
            usage=result.get("usage") or {},
        )
