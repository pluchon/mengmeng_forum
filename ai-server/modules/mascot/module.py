"""将现有看板娘图接入统一模块契约。"""

from __future__ import annotations

import asyncio
import re
from typing import Any

from config import settings
from graphs.mascot_graph import run_mascot_chat
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult

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
            },
            usage=result.get("usage") or {},
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
    appearance = re.sub(r"[^a-z0-9_-]", "", str(raw.get("appearance") or "snow_miku").lower())
    return {
        "message": message,
        "session_id": str(raw.get("sessionId") or raw.get("session_id") or ""),
        "appearance": (appearance or "snow_miku")[:64],
        "tier": tier if tier in _ALLOWED_TIERS else "basic",
        "history": _clean_history(raw.get("history"), max_length),
        "llm_provider": str(raw.get("llmProvider") or raw.get("llm_provider") or "").strip(),
        "skill": skill if skill in _ALLOWED_SKILLS else "chat",
        "vip_tier": _parse_vip_tier(raw.get("vipTier", raw.get("vip_tier", 0))),
        "client_datetime": str(raw.get("clientDatetime") or raw.get("client_datetime") or "").strip()[:64],
    }


def _clean_history(raw: Any, max_length: int) -> list[dict[str, str]]:
    history: list[dict[str, str]] = []
    if not isinstance(raw, list):
        return history
    for item in raw[-16:]:
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
