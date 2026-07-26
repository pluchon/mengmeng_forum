"""将现有文章审核图接入统一模块契约。"""

from __future__ import annotations

import asyncio
from typing import Any

from graphs.article_audit import run_audit
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult


class ContentModerationModule:
    """审核只返回 AI 结论，Java 继续拥有最终状态流转。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        task = self._to_audit_task(request)
        result = await asyncio.to_thread(run_audit, task)
        final_status = str(result.get("finalStatus") or "AUDIT_ERROR")
        risk_level, action_suggestion = self._to_risk(final_status)
        return ModuleResult(
            success=True,
            data={
                "riskLevel": risk_level,
                "categories": [],
                "reason": str(result.get("finalReason") or ""),
                "confidence": None,
                "actionSuggestion": action_suggestion,
                "audit": result,
            },
        )

    @staticmethod
    def _to_audit_task(request: ModuleRequest) -> dict[str, Any]:
        payload = request.payload
        article_id = payload.get("articleId")
        try:
            normalized_article_id = int(article_id)
        except (TypeError, ValueError) as exc:
            raise ModuleRequestError("INVALID_AUDIT_PAYLOAD", "articleId 必须是正整数") from exc
        if normalized_article_id <= 0:
            raise ModuleRequestError("INVALID_AUDIT_PAYLOAD", "articleId 必须是正整数")
        return {
            "taskId": str(payload.get("taskId") or request.request_id),
            "articleId": normalized_article_id,
            "userId": payload.get("userId"),
            "title": str(payload.get("title") or ""),
            "content": str(payload.get("content") or ""),
            "coverUrl": payload.get("coverUrl"),
            "imageUrls": payload.get("imageUrls") or [],
            "videoUrl": payload.get("videoUrl"),
            "submittedAt": payload.get("submittedAt") or 0,
        }

    @staticmethod
    def _to_risk(final_status: str) -> tuple[str, str]:
        if final_status == "APPROVED":
            return "PASS", "ALLOW"
        if final_status == "REJECTED":
            return "BLOCK", "REJECT"
        return "REVIEW", "MANUAL_REVIEW"
