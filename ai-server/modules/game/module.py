"""规则优先的游戏 AI 模块。"""

from __future__ import annotations

import asyncio

from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from services.ai_hub_service import generate_gobang_move, generate_jinzi_move


class GobangMoveModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        board = request.payload.get("board")
        ai_chess = _parse_ai_chess(request)
        model_code = str(request.payload.get("modelCode") or request.payload.get("model_code") or "").strip()
        use_llm = bool(request.payload.get("useLlm", request.payload.get("use_llm", True)))
        move = await asyncio.to_thread(generate_gobang_move, board, ai_chess, model_code, use_llm=use_llm)
        return ModuleResult(success=True, data=move, usage=move.get("usage") or {})


class JiziMoveModule:
    async def run(self, request: ModuleRequest) -> ModuleResult:
        board = request.payload.get("board")
        ai_chess = _parse_ai_chess(request)
        model_code = str(request.payload.get("modelCode") or request.payload.get("model_code") or "").strip()
        move = await asyncio.to_thread(generate_jinzi_move, board, ai_chess, model_code, use_llm=False)
        return ModuleResult(success=True, data=move, usage=move.get("usage") or {})


def _parse_ai_chess(request: ModuleRequest) -> int:
    try:
        value = int(request.payload.get("aiChess", request.payload.get("ai_chess", 2)))
    except (TypeError, ValueError) as exc:
        raise ModuleRequestError("INVALID_GAME_PAYLOAD", "aiChess 必须为 1 或 2") from exc
    if value not in {1, 2}:
        raise ModuleRequestError("INVALID_GAME_PAYLOAD", "aiChess 必须为 1 或 2")
    return value
