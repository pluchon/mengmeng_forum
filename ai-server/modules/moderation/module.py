"""将现有文章审核图接入统一模块契约。"""

from __future__ import annotations

import asyncio
import base64
import logging
import time
from typing import Any

from langchain_core.messages import HumanMessage
from clients.llm import text_llm, vision_llm, vision_llm_fallback
from config import settings
from graphs.article_audit import run_audit
from graphs.prompts import IMAGE_AUDIT_TEMPLATE, IMAGE_DESC_PROMPT, TEXT_AUDIT_TEMPLATE
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from utils import cache as semantic_cache
from utils.html import clean_html
from utils.image import fetch_image_bytes, meets_vision_model_min_size, to_data_url, validate_image_bytes
from modules.moderation.graph import run_text_moderation

logger = logging.getLogger(__name__)

_VISION_RETRY_DELAYS_SEC = (0.0, 1.2, 2.5)


def _is_retryable_vision_error(exc: BaseException) -> bool:
    message = str(exc).lower()
    if any(token in message for token in ("429", "too many requests", "rate limit", "throttl", "timeout")):
        return True
    response = getattr(exc, "response", None)
    if response is not None:
        status = getattr(response, "status_code", None)
        if status in {429, 500, 502, 503, 504}:
            return True
    return False


def _invoke_vision(message: HumanMessage):
    last_exc: BaseException | None = None
    for delay in _VISION_RETRY_DELAYS_SEC:
        if delay > 0:
            time.sleep(delay)
        try:
            return vision_llm().invoke([message])
        except Exception as exc:
            last_exc = exc
            if not _is_retryable_vision_error(exc):
                break
    try:
        return vision_llm_fallback().invoke([message])
    except Exception as exc:
        last_exc = exc
    raise ModuleRequestError(
        "VISION_AUDIT_UNAVAILABLE",
        "图片审核服务暂时不可用，请稍后重试",
    ) from last_exc


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
        logger.info("TEXT_AUDIT 收到审核请求 contentLen=%s", len(plain))
        cached = semantic_cache.find_match(plain)
        if cached:
            return ModuleResult(
                success=True,
                data={"allowed": bool(cached["allow"]), "reason": cached.get("msg", ""), "cached": True},
            )

        decision = await asyncio.to_thread(
            run_text_moderation,
            str(request.payload.get("title") or ""),
            plain,
        )
        allowed = bool(decision["allowed"])
        reason = str(decision.get("reason") or "")
        semantic_cache.save(plain, {"allow": allowed, "msg": reason or "OK"})
        return ModuleResult(success=True, data={
            "allowed": allowed,
            "reason": reason,
            "confidence": decision.get("confidence"),
            "category": decision.get("category"),
            "deepUsed": decision.get("deepUsed", False),
            "cached": False,
        })

    @staticmethod
    async def _audit_image(request: ModuleRequest) -> ModuleResult:
        payload = request.payload if isinstance(request.payload, dict) else {}
        items = payload.get("items")
        if not isinstance(items, list):
            image_urls = payload.get("imageUrls")
            if isinstance(image_urls, list) and image_urls:
                items = [{"imageUrl": url, "objectKey": ""} for url in image_urls if isinstance(url, str)]
        if isinstance(items, list) and items:
            return await ContentModerationModule._audit_image_batch(items)

        image_data = await ContentModerationModule._load_image_audit_bytes(payload)
        allowed, reason = await asyncio.to_thread(ContentModerationModule._audit_image_data, image_data)
        return ModuleResult(success=True, data={"allowed": allowed, "reason": reason})

    @staticmethod
    async def _audit_image_batch(items: list[Any]) -> ModuleResult:
        max_workers = int(settings.audit.get("max_image_workers", 3))
        if max_workers < 1:
            max_workers = 1
        sem = asyncio.Semaphore(max_workers)
        # 同请求内按 URL/key 去重，避免重复打视觉模型
        unique: dict[str, dict[str, Any]] = {}
        order: list[str] = []
        normalized_items: list[dict[str, Any]] = []
        for raw in items[:9]:
            if not isinstance(raw, dict):
                continue
            image_url = raw.get("imageUrl") if isinstance(raw.get("imageUrl"), str) else ""
            object_key = raw.get("objectKey") if isinstance(raw.get("objectKey"), str) else ""
            image_url = image_url.strip()
            object_key = object_key.strip()
            key = object_key or image_url
            if not key:
                continue
            normalized_items.append({"imageUrl": image_url, "objectKey": object_key, "dedupe": key})
            if key not in unique:
                unique[key] = {"imageUrl": image_url, "objectKey": object_key}
                order.append(key)

        async def _one(item: dict[str, Any]) -> dict[str, Any]:
            async with sem:
                try:
                    image_data = await ContentModerationModule._load_image_audit_bytes(item)
                    allowed, reason = await asyncio.to_thread(
                        ContentModerationModule._audit_image_data, image_data
                    )
                    return {"allowed": bool(allowed), "reason": str(reason or "")}
                except ModuleRequestError as exc:
                    return {"allowed": False, "reason": str(exc.message or exc)}
                except Exception as exc:  # noqa: BLE001
                    logger.warning("IMAGE_AUDIT 批量单项失败: %s", exc)
                    return {"allowed": False, "reason": "图片审核服务暂时不可用，请稍后重试"}

        tasks = [asyncio.create_task(_one(unique[k])) for k in order]
        done = await asyncio.gather(*tasks)
        by_key = {order[i]: done[i] for i in range(len(order))}
        results: list[dict[str, Any]] = []
        all_allowed = True
        for index, item in enumerate(normalized_items):
            row = dict(by_key.get(item["dedupe"], {"allowed": False, "reason": "图片审核失败，请稍后重试"}))
            row["index"] = index
            row["imageUrl"] = item["imageUrl"]
            row["objectKey"] = item["objectKey"]
            if not row.get("allowed"):
                all_allowed = False
            results.append(row)
        return ModuleResult(
            success=True,
            data={"allowed": all_allowed, "results": results},
        )

    @staticmethod
    async def _load_image_audit_bytes(payload: dict[str, Any]) -> bytes:
        raw_key = payload.get("objectKey")
        object_key = raw_key.strip() if isinstance(raw_key, str) else ""
        raw_url = payload.get("imageUrl")
        image_url = raw_url.strip() if isinstance(raw_url, str) else ""
        if object_key or image_url:
            def _fetch() -> bytes | None:
                return fetch_image_bytes(
                    image_url,
                    15,
                    object_key=object_key or None,
                )

            image_data = await asyncio.to_thread(_fetch)
            if not image_data:
                logger.warning(
                    "IMAGE_AUDIT 读取失败 url=%s key=%s",
                    image_url[:120],
                    object_key[:80],
                )
                raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "图片读取失败，请稍后再试")
            return image_data
        encoded = payload.get("contentBase64")
        if not isinstance(encoded, str) or not encoded.strip():
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "图片内容为空，请重新上传")
        try:
            image_data = base64.b64decode(encoded, validate=True)
        except (ValueError, TypeError) as exc:
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "图片数据损坏，请重新上传") from exc
        return image_data

    @staticmethod
    def _audit_image_data(image_data: bytes) -> tuple[bool, str]:
        max_bytes = int(settings.image.get("max_bytes", 10 * 1024 * 1024))
        if len(image_data) > max_bytes:
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", "图片太大了，请压缩后再上传")
        fmt = validate_image_bytes(image_data)
        if not fmt:
            head = image_data[:12].hex() if image_data else ""
            logger.warning("IMAGE_AUDIT 无法识别图片 bytes=%d head=%s", len(image_data), head)
            message = _format_reject_message(image_data)
            raise ModuleRequestError("INVALID_IMAGE_AUDIT_PAYLOAD", message)
        if not meets_vision_model_min_size(image_data):
            raise ModuleRequestError(
                "INVALID_IMAGE_AUDIT_PAYLOAD",
                "图片尺寸太小，请换一张更清晰的图片",
            )
        data_url = to_data_url(image_data, fmt)
        message = HumanMessage(content=[{"image": data_url}, {"text": IMAGE_DESC_PROMPT}])
        try:
            response = _invoke_vision(message)
        except ModuleRequestError:
            raise
        except Exception as exc:
            logger.warning("IMAGE_AUDIT 视觉模型调用失败: %s", exc)
            raise ModuleRequestError(
                "VISION_AUDIT_UNAVAILABLE",
                "图片审核服务暂时不可用，请稍后重试",
            ) from exc
        description = _extract_text(response)
        if not description:
            return False, "图片无法识别，请换一张图片"
        try:
            verdict = _extract_text((IMAGE_AUDIT_TEMPLATE | text_llm()).invoke({"desc": description}))
        except Exception as exc:
            logger.warning("IMAGE_AUDIT 文本判定失败: %s", exc)
            raise ModuleRequestError(
                "VISION_AUDIT_UNAVAILABLE",
                "图片审核服务暂时不可用，请稍后重试",
            ) from exc
        return verdict.startswith("是"), verdict

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


def _format_reject_message(image_data: bytes) -> str:
    if len(image_data) >= 12 and image_data[4:8] == b"ftyp":
        brand = image_data[8:12].decode("ascii", errors="ignore").lower()
        if brand.startswith("hei") or brand == "mif1" or brand.startswith("avif"):
            return "暂不支持这种图片格式，请转成 JPG 或 PNG 后再上传"
    return "暂不支持这种图片格式，请使用 JPG、PNG 或 GIF"


def _extract_text(response: object) -> str:
    content = getattr(response, "content", response)
    if isinstance(content, list) and content:
        first = content[0]
        return str(first.get("text", "") if isinstance(first, dict) else first).strip()
    return str(content or "").strip()
