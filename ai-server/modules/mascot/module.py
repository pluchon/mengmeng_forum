"""将现有看板娘图接入统一模块契约。"""

from __future__ import annotations

import asyncio
import json
import re
from collections.abc import Iterator
from typing import Any

from pydantic import BaseModel, Field

from clients.dashscope_chat_client import json_chat_completion
from clients.llm import flash_model_name
from config import settings
from graphs.mascot_graph import run_mascot_chat, stream_mascot_chat
from graphs.mascot_context_graph import compress_mascot_context
from graphs.mascot_intent_match import match_intent_pairs
from runtime.contracts import ModuleEvent, ModuleRequest, ModuleRequestError, ModuleResult

_ALLOWED_SKILLS = {"writing", "help", "chat"}
_ALLOWED_TIERS = {"basic", "vip"}


class MascotChatModule:
    """仅编排 AI 对话；会话、配额、积分与持久化仍在 Java。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        payload = _normalize_payload(request.payload)
        result = await asyncio.to_thread(run_mascot_chat, **payload)
        return ModuleResult(
            success=True,
            data={
                "reply": result.get("reply") or "",
                "live2d": result.get("live2d") or {},
                "suggestedAppearance": result.get("suggested_appearance"),
                "mcpUsed": bool(result.get("mcp_used")),
                "action": result.get("action") or "CHAT",
                "imagePrompt": result.get("image_prompt") or "",
                "imageQuality": result.get("image_quality") or "normal",
                "complexity": result.get("complexity") or "SIMPLE",
                "relatedSearchOffer": bool(result.get("related_search_offer")),
                "relatedSearchQuery": result.get("related_search_query") or "",
                "askConfirmOffer": result.get("ask_offer") or {},
                "searchImageGallery": result.get("search_image_gallery") or [],
                "memoryWrite": result.get("memory_write") or {},
            },
            usage=result.get("usage") or {},
        )

    def stream(self, request: ModuleRequest) -> Iterator[ModuleEvent]:
        """把 LangGraph 节点状态与模型文本转换为统一 Gateway 事件。"""
        payload = _normalize_payload(request.payload)
        for event_type, data in stream_mascot_chat(**payload):
            if event_type == "status":
                yield ModuleEvent("progress", {"status": str(data or "preparing")})
            elif event_type == "text":
                yield ModuleEvent("text", {"text": str(data or "")})
            elif event_type == "meta" and isinstance(data, dict):
                yield ModuleEvent("meta", data)
            elif event_type == "usage" and isinstance(data, dict):
                yield ModuleEvent("usage", data)



class MascotIntentMatchModule:
    """判断两条牵线意愿能不能互相帮上。

    刻意只收两段脱敏文本，**不接收也不需要 userId**——身份、可见性、要不要通知
    全在 Java。这边给出的只是「配不配 + 一句交集描述」，那句描述会原样发给双方，
    所以不能出现任何指向具体某个人的说法。
    """

    async def run(self, request: ModuleRequest) -> ModuleResult:
        pairs = request.payload.get("pairs")
        if not isinstance(pairs, list) or not pairs:
            raise ModuleRequestError("INVALID_MATCH_PAYLOAD", "pairs 不能为空")
        if len(pairs) > 30:
            raise ModuleRequestError("INVALID_MATCH_PAYLOAD", "单次最多判定 30 对")
        cleaned = []
        for item in pairs:
            if not isinstance(item, dict):
                continue
            a = str(item.get("a") or "").strip()[:120]
            b = str(item.get("b") or "").strip()[:120]
            key = str(item.get("key") or "").strip()[:64]
            if a and b and key:
                cleaned.append({"key": key, "a": a, "b": b})
        if not cleaned:
            return ModuleResult(success=True, data={"results": []}, usage={})
        results, usage = await asyncio.to_thread(match_intent_pairs, cleaned)
        return ModuleResult(success=True, data={"results": results}, usage=usage)

class MascotContextCompressModule:
    """看板娘会话记忆压缩；持久化仍由 Java 会话服务负责。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        history = _clean_history(request.payload.get("history"), 6000)
        if not history:
            raise ModuleRequestError("INVALID_MASCOT_CONTEXT", "没有可压缩的会话内容")
        result = await asyncio.to_thread(compress_mascot_context, history)
        summary = str(result.get("summary") or "").strip()
        if not summary:
            raise ModuleRequestError("MASCOT_CONTEXT_EMPTY", "上下文压缩结果为空")
        return ModuleResult(success=True, data={"summary": summary}, usage=result.get("usage") or {})


class MascotMemoryEditPayload(BaseModel):
    summary: str = Field(default="", max_length=240)
    facts: list[str] = Field(default_factory=list, max_length=10)


class MascotMemoryEditModule:
    """看板娘长期记忆编辑；持久化仍由 Java 负责。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        summary = str(request.payload.get("memory_summary") or "").strip()[:240]
        facts = _string_list(request.payload.get("memory_facts"), 10, 40)
        instruction = str(request.payload.get("memory_edit_instruction") or "").strip()[:200]
        if not instruction:
            raise ModuleRequestError("INVALID_MASCOT_MEMORY", "记忆修改内容不能为空")
        prompt = (
            "你是论坛看板娘的长期记忆编辑节点。只根据用户的修改指令更新一份简短长期记忆。"
            "summary 保留稳定偏好和长期有效信息，最多 240 字；facts 最多 10 条，每条不超过 40 字。"
            "不要写寒暄，不要把临时情绪、一次性任务、联网资料、猜测内容写进记忆。只输出合法 JSON。"
            f"\n当前 summary：{summary or '（空）'}"
            f"\n当前 facts：{json.dumps(facts, ensure_ascii=False)}"
            f"\n用户指令：{instruction}"
        )
        raw, usage = await asyncio.to_thread(
            json_chat_completion,
            flash_model_name(),
            [
                {"role": "system", "content": "你是受控工作流节点。必须只输出一个合法 JSON 对象。"},
                {"role": "user", "content": prompt},
            ],
            temperature=0.1,
            timeout=120,
        )
        try:
            parsed = MascotMemoryEditPayload.model_validate_json(raw)
        except Exception as exc:
            raise ModuleRequestError("MASCOT_MEMORY_INVALID", "记忆更新结果无效") from exc
        return ModuleResult(
            success=True,
            data={
                "summary": parsed.summary.strip()[:240],
                "facts": _string_list(parsed.facts, 10, 40),
            },
            usage=usage or {},
        )


def _normalize_payload(raw: dict[str, Any]) -> dict[str, Any]:
    message = str(raw.get("message") or "").strip()
    max_length = int(settings.mascot.get("max_user_message_len", 2000))
    if not message:
        raise ModuleRequestError("INVALID_MASCOT_PAYLOAD", "message 不能为空")
    if len(message) > max_length:
        raise ModuleRequestError("INVALID_MASCOT_PAYLOAD", f"message 不能超过 {max_length} 字符")
    skill = str(raw.get("skill") or "chat").strip().lower()
    tier = str(raw.get("tier") or "basic").strip().lower()
    appearance = re.sub(r"[^a-z0-9_-]", "", str(raw.get("appearance") or "xiaomeng").lower())
    return {
        "message": message,
        "session_id": str(raw.get("sessionId") or raw.get("session_id") or ""),
        "appearance": (appearance or "xiaomeng")[:64],
        "tier": tier if tier in _ALLOWED_TIERS else "basic",
        "history": _clean_history(raw.get("history"), max_length),
        "llm_provider": str(raw.get("llmProvider") or raw.get("llm_provider") or "").strip(),
        "skill": skill if skill in _ALLOWED_SKILLS else "chat",
        "vip_tier": _parse_vip_tier(raw.get("vipTier", raw.get("vip_tier", 0))),
        "client_datetime": str(raw.get("clientDatetime") or raw.get("client_datetime") or "").strip()[:64],
        "memory_summary": str(raw.get("memorySummary") or raw.get("memory_summary") or "").strip()[:240],
        "memory_facts": _string_list(raw.get("memoryFacts") or raw.get("memory_facts"), 10, 40),
        # 由 Java 决定这一轮要不要探长期记忆；缺省为 True 兼容旧调用方
        "memory_probe": bool(raw.get("memoryProbe", raw.get("memory_probe", True))),
        # 这一轮准不准问「要不要我留意一下」；由 Java 按会话与上限决定，默认不问
        "intent_probe": bool(raw.get("intentProbe", raw.get("intent_probe", False))),
        # 压缩摘要走独立字段，不混进 history——history 会被窗口截断
        "context_summary": str(raw.get("contextSummary") or raw.get("context_summary") or "").strip()[:2000],
        "liked_titles": _string_list(raw.get("likedTitles") or raw.get("liked_titles"), 6, 80),
        "favorite_songs": _string_list(raw.get("favoriteSongs") or raw.get("favorite_songs"), 6, 80),
    }


def _clean_history(raw: Any, max_length: int) -> list[dict[str, str]]:
    history: list[dict[str, str]] = []
    if not isinstance(raw, list):
        return history
    window = int(settings.mascot.get("max_history_messages", 16))
    for item in raw[-window:]:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role") or "").strip().lower()
        content = str(item.get("content") or "").strip()
        if role in {"user", "assistant"} and content:
            history.append({"role": role, "content": content[:max_length]})
    return history


def _parse_vip_tier(raw: Any) -> int:
    try:
        return max(0, min(2, int(raw)))
    except (TypeError, ValueError):
        return 0


def _string_list(raw: Any, limit: int, item_len: int) -> list[str]:
    if not isinstance(raw, list):
        return []
    out: list[str] = []
    for item in raw:
        text = str(item or "").strip()[:item_len]
        if text and text not in out:
            out.append(text)
        if len(out) >= limit:
            break
    return out
