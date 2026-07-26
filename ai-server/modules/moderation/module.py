"""将现有文章审核图接入统一模块契约。"""

from __future__ import annotations

import asyncio
import base64
from typing import Any

from langchain_core.messages import HumanMessage
from clients.llm import text_llm, vision_llm, vision_llm_fallback
from config import settings
from graphs.article_audit import run_audit
from graphs.prompts import IMAGE_AUDIT_TEMPLATE, IMAGE_DESC_PROMPT, TEXT_AUDIT_TEMPLATE
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils import cache as semantic_cache
from utils.html import clean_html
from utils.image import to_data_url, validate_image_bytes


class ContentModerationModule:
    """审核只返回 AI 结论，Java 继续拥有最终状态流转。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        if request.intent == "TEXT_AUDIT":
            return await self._audit_text(request)
        if request.intent == "IMAGE_AUDIT":
            return await self._audit_image(request)
        if request.intent != "ARTICLE_AUDIT":
            raise ModuleRequestError("UNSUPPORTED_AUDIT_INTENT", "不支持的审核意图")
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
    async def _audit_text(request: ModuleRequest) -> ModuleResult:
        content = request.payload.get("content")
        if not isinstance(content, str):
            raise ModuleRequestError("INVALID_TEXT_AUDIT_PAYLOAD", "content 必须是文本")
        plain = clean_html(content).strip()
        if not plain:
            return ModuleResult(success=True, data={"allowed": True, "reason": "", "cached": False})
        cached = semantic_cache.find_match(plain)
        if cached:
            return ModuleResult(
                success=True,
                data={"allowed": bool(cached["allow"]), "reason": cached.get("msg", ""), "cached": True},
            )

        def invoke() -> tuple[bool, str]:
            response = (TEXT_AUDIT_TEMPLATE | text_llm()).invoke(
                {"title": str(request.payload.get("title") or ""), "text": plain}
            )
            raw = _extract_text(response)
            allowed = raw.upper() == "YES"
            return allowed, "" if allowed else raw

        allowed, reason = await asyncio.to_thread(invoke)
        semantic_cache.save(plain, {"allow": allowed, "msg": reason or "OK"})
        return ModuleResult(success=True, data={"allowed": allowed, "reason": reason, "cached": False})

    @staticmethod
    async def _audit_image(request: ModuleRequest) -> ModuleResult:
        encoded = request.payload.get("contentBase64")
        if not isinstance(encoded, str) or not encoded.strip():
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "contentBase64 不能为空")
        try:
            image_data = base64.b64decode(encoded, validate=True)
        except (ValueError, TypeError) as exc:
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "contentBase64 格式错误") from exc
        max_bytes = int(settings.image.get("max_bytes", 10 * 1024 * 1024))
        if len(image_data) > max_bytes:
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "图片超过允许大小")
        fmt = validate_image_bytes(image_data)
        if not fmt:
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "不支持的图片格式")

        def invoke() -> tuple[bool, str]:
            data_url = to_data_url(image_data, fmt)
            message = HumanMessage(content=[{"image": data_url}, {"text": IMAGE_DESC_PROMPT}])
            try:
                response = vision_llm().invoke([message])
            except Exception:
                response = vision_llm_fallback().invoke([message])
            description = _extract_text(response)
            if not description:
                return False, "图片描述为空"
            verdict = _extract_text((IMAGE_AUDIT_TEMPLATE | text_llm()).invoke({"desc": description}))
            return verdict.startswith("是"), verdict

        allowed, reason = await asyncio.to_thread(invoke)
        return ModuleResult(success=True, data={"allowed": allowed, "reason": reason})

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


def _extract_text(response: object) -> str:
    content = getattr(response, "content", response)
    if isinstance(content, list) and content:
        first = content[0]
        return str(first.get("text", "") if isinstance(first, dict) else first).strip()
    return str(content or "").strip()
