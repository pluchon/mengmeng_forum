"""棋类 AI 的规则与模型落子能力。"""
from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings

logger = logging.getLogger(__name__)

_P0_WIN = 0
_P1_BLOCK_FIVE = 1
_P2_BLOCK_FOUR = 2
_P3_ATTACK = 3
_P4_BLOCK_LIVE_THREE = 4


def generate_gobang_move(
    board: list[list[int]],
    ai_chess: int = 2,
    model_code: str | None = None,
    *,
    use_llm: bool = True,
    insight: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """五子棋落子：威胁优先级与本地搜索优先；有 insight 时 LLM 仅在候选中选点。"""
    safe_board = _normalize_gobang_board(board)
    player_chess = 1 if ai_chess == 2 else 2
    model = _resolve_gobang_model(settings.dashscope, model_code)
    threat = _find_best_threat(safe_board, ai_chess, player_chess)
    if threat is not None and threat[0] <= _P2_BLOCK_FOUR:
        return {
            "row": threat[1],
            "col": threat[2],
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "threat_local",
            "fallback": False,
            "pickReason": threat[3],
            "usage": {"model_code": model, "estimated": True, "local": True},
        }
    candidates = _extract_candidates(insight)
    if use_llm and candidates:
        return _choose_llm_from_candidates(
            safe_board,
            ai_chess,
            player_chess,
            model,
            insight or {},
            candidates,
        )
    if not use_llm or not candidates:
        row, col = _choose_local_gobang_move(safe_board, ai_chess, player_chess)
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "heuristic_local",
            "fallback": False,
            "usage": {"model_code": model, "estimated": True, "local": True},
        }
    return _choose_llm_legacy(safe_board, ai_chess, player_chess, model)


def _choose_llm_from_candidates(
    board: list[list[int]],
    ai_chess: int,
    player_chess: int,
    model: str,
    insight: dict[str, Any],
    candidates: list[dict[str, Any]],
) -> dict[str, Any]:
    system = (
        "你是五子棋助手。只能从 candidateMoves 中选一个 row/col；"
        "若无把握则选 score 最高的点。"
        '只输出 JSON：{"row":8,"col":8,"pickReason":"挡对手活四"}'
    )
    user = {
        "ai_chess": ai_chess,
        "phase": insight.get("phase"),
        "moveNo": insight.get("moveNo") or insight.get("move_no"),
        "myThreats": insight.get("myThreats") or insight.get("my_threats") or [],
        "oppThreats": insight.get("oppThreats") or insight.get("opp_threats") or [],
        "candidateMoves": candidates,
        "stones": insight.get("stones") or _sparse_board_stones(board),
    }
    usage: dict[str, Any] = {"model_code": model, "estimated": True}
    try:
        content, usage = dashscope_chat_completion(
            model,
            [
                {"role": "system", "content": system},
                {"role": "user", "content": json.dumps(user, ensure_ascii=False)},
            ],
            timeout=8,
            temperature=0,
        )
        row, col, pick_reason = _parse_gobang_pick(content)
        if not _is_candidate(candidates, row, col):
            best = max(candidates, key=lambda item: int(item.get("score") or 0))
            row, col = int(best["row"]), int(best["col"])
            pick_reason = "fallback_highest_score"
        if not _is_gobang_empty(board, row, col):
            raise ValueError("Qwen 返回了非法五子棋坐标")
        must_block = _find_must_block(board, ai_chess, player_chess)
        if must_block is not None and (must_block[0] != row or must_block[1] != col):
            return {
                "row": must_block[0],
                "col": must_block[1],
                "model": model,
                "model_name": model,
                "model_version": model,
                "strategy_name": "llm_must_block_guard",
                "fallback": True,
                "pickReason": must_block[2],
                "usage": {**usage, "fallback_reason": "must_block_override"},
            }
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "llm_candidate_pick",
            "fallback": False,
            "pickReason": pick_reason,
            "usage": usage,
        }
    except Exception as exc:
        logger.warning("Qwen 五子棋不可用，使用 Python 本地策略兜底: %s", exc)
        row, col = _choose_local_gobang_move(board, ai_chess, player_chess)
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "heuristic_local",
            "fallback": True,
            "usage": {**usage, "fallback_reason": str(exc)[:160]},
        }


def _choose_llm_legacy(
    board: list[list[int]],
    ai_chess: int,
    player_chess: int,
    model: str,
) -> dict[str, Any]:
    stones = _sparse_board_stones(board)
    system = (
        "你是五子棋 AI。row/col 从 0 开始，1=黑 2=白。"
        "选空位落子。优先：自己五连、挡对手五连、活四冲四。"
        '只输出 JSON：{"row":7,"col":7,"pickReason":"简短理由"}'
    )
    user = {
        "ai_chess": ai_chess,
        "stones": stones,
        "board_size": 15,
    }
    usage: dict[str, Any] = {"model_code": model, "estimated": True}
    try:
        content, usage = dashscope_chat_completion(
            model,
            [
                {"role": "system", "content": system},
                {"role": "user", "content": json.dumps(user, ensure_ascii=False)},
            ],
            timeout=8,
            temperature=0,
        )
        row, col, pick_reason = _parse_gobang_pick(content)
        if not _is_gobang_empty(board, row, col):
            raise ValueError("Qwen 返回了非法五子棋坐标")
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "llm_with_rule_guard",
            "fallback": False,
            "pickReason": pick_reason,
            "usage": usage,
        }
    except Exception as exc:
        logger.warning("Qwen 五子棋不可用，使用 Python 本地策略兜底: %s", exc)
        row, col = _choose_local_gobang_move(board, ai_chess, player_chess)
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "heuristic_local",
            "fallback": True,
            "usage": {**usage, "fallback_reason": str(exc)[:160]},
        }


def _extract_candidates(insight: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not isinstance(insight, dict):
        return []
    raw = insight.get("candidateMoves") or insight.get("candidate_moves") or []
    if not isinstance(raw, list):
        return []
    rows: list[dict[str, Any]] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        try:
            row = int(item.get("row"))
            col = int(item.get("col"))
        except (TypeError, ValueError):
            continue
        rows.append(
            {
                "row": row,
                "col": col,
                "reason": str(item.get("reason") or ""),
                "score": int(item.get("score") or 0),
            }
        )
    return rows


def _is_candidate(candidates: list[dict[str, Any]], row: int, col: int) -> bool:
    return any(int(item["row"]) == row and int(item["col"]) == col for item in candidates)


def _sparse_board_stones(board: list[list[int]]) -> list[dict[str, int]]:
    stones: list[dict[str, int]] = []
    for row in range(len(board)):
        for col in range(len(board[row])):
            cell = board[row][col]
            if cell in (1, 2):
                stones.append({"row": row, "col": col, "chess": int(cell)})
    return stones


# Java 侧按玩家段位选档，请求里带的是档位而不是某个具体模型。
# 只比对 == pro 是危险的：那等于要求 Java 的硬编码和这里的配置值一字不差，
# 运维在 config 里换个深度模型，高段位玩家就会静默退回 flash，没有任何报错。
_GOBANG_DEEP_ALIASES = {"deep", "pro", "max", "qwen3.7-max"}


def _resolve_gobang_model(dashscope: dict[str, Any], model_code: str | None) -> str:
    requested = (model_code or "").strip()
    flash = str(dashscope.get("model_text_flash") or dashscope.get("model_text") or "qwen3.7-flash").strip()
    pro = str(dashscope.get("model_text_deep") or "qwen3.7-max").strip()
    if requested and requested.lower() in _GOBANG_DEEP_ALIASES | {pro.lower()}:
        return pro
    return flash


def _normalize_gobang_board(board: list[list[int]]) -> list[list[int]]:
    if not isinstance(board, list) or len(board) != 15:
        raise ValueError("board must be 15x15")
    safe: list[list[int]] = []
    for row in board:
        if not isinstance(row, list) or len(row) != 15:
            raise ValueError("board must be 15x15")
        safe_row: list[int] = []
        for cell in row:
            try:
                value = int(cell)
            except (TypeError, ValueError):
                value = 0
            safe_row.append(value if value in (0, 1, 2) else 0)
        safe.append(safe_row)
    return safe


def _parse_gobang_pick(content: str) -> tuple[int, int, str]:
    text = (content or "").strip()
    match = re.search(r"\{[\s\S]*\}", text)
    if not match:
        raise ValueError("Qwen 未返回 JSON 坐标")
    data = json.loads(match.group(0))
    row = int(data.get("row"))
    col = int(data.get("col"))
    pick_reason = str(data.get("pickReason") or data.get("pick_reason") or "").strip()
    return row, col, pick_reason


def _is_gobang_empty(board: list[list[int]], row: int, col: int) -> bool:
    return 0 <= row < 15 and 0 <= col < 15 and board[row][col] == 0


def _find_best_threat(
    board: list[list[int]],
    my_chess: int,
    opp_chess: int,
) -> tuple[int, int, int, str] | None:
    hit = _find_placement(board, my_chess, _P0_WIN, "win_five")
    if hit is not None:
        return hit
    hit = _find_placement(board, opp_chess, _P1_BLOCK_FIVE, "block_five")
    if hit is not None:
        return hit
    hit = _find_placement(board, opp_chess, _P2_BLOCK_FOUR, "block_open_or_rush_four")
    if hit is not None:
        return hit
    hit = _find_attack(board, my_chess)
    if hit is not None:
        return hit
    return _find_placement(board, opp_chess, _P4_BLOCK_LIVE_THREE, "block_live_three")


def _find_must_block(
    board: list[list[int]],
    my_chess: int,
    opp_chess: int,
) -> tuple[int, int, str] | None:
    hit = _find_placement(board, opp_chess, _P1_BLOCK_FIVE, "block_five")
    if hit is not None:
        return hit[1], hit[2], hit[3]
    hit = _find_placement(board, opp_chess, _P2_BLOCK_FOUR, "block_open_or_rush_four")
    if hit is not None:
        return hit[1], hit[2], hit[3]
    return None


def _find_attack(board: list[list[int]], my_chess: int) -> tuple[int, int, int, str] | None:
    open_four: tuple[int, int, int, str] | None = None
    double_three: tuple[int, int, int, str] | None = None
    for row in range(15):
        for col in range(15):
            if board[row][col] != 0 or not _has_neighbor(board, row, col):
                continue
            board[row][col] = my_chess
            stats = _analyze(board, row, col, my_chess)
            board[row][col] = 0
            if stats["open_fours"] > 0 and open_four is None:
                open_four = (_P3_ATTACK, row, col, "make_open_four")
            elif stats["live_threes"] >= 2 and double_three is None:
                double_three = (_P3_ATTACK, row, col, "make_double_live_three")
    return open_four or double_three


def _find_placement(
    board: list[list[int]],
    chess: int,
    priority: int,
    reason: str,
) -> tuple[int, int, int, str] | None:
    for row in range(15):
        for col in range(15):
            if board[row][col] != 0:
                continue
            if priority >= _P2_BLOCK_FOUR and not _has_neighbor(board, row, col):
                continue
            board[row][col] = chess
            stats = _analyze(board, row, col, chess)
            board[row][col] = 0
            matched = False
            if priority in (_P0_WIN, _P1_BLOCK_FIVE):
                matched = stats["fives"] > 0
            elif priority == _P2_BLOCK_FOUR:
                matched = stats["open_fours"] > 0 or stats["rush_fours"] > 0
            elif priority == _P4_BLOCK_LIVE_THREE:
                matched = stats["live_threes"] > 0
            if matched:
                return priority, row, col, reason
    return None


def _analyze(board: list[list[int]], row: int, col: int, chess: int) -> dict[str, int]:
    fives = 0
    open_fours = 0
    rush_fours = 0
    live_threes = 0
    for row_step, col_step in ((1, 0), (0, 1), (1, 1), (1, -1)):
        forward = _count_direction(board, chess, row, col, row_step, col_step)
        backward = _count_direction(board, chess, row, col, -row_step, -col_step)
        length = forward + backward + 1
        open_ends = _open_end(board, chess, row, col, row_step, col_step, forward) + _open_end(
            board, chess, row, col, -row_step, -col_step, backward
        )
        if length >= 5:
            fives += 1
        elif length == 4 and open_ends == 2:
            open_fours += 1
        elif length == 4 and open_ends == 1:
            rush_fours += 1
        elif length == 3 and open_ends == 2:
            live_threes += 1
    return {
        "fives": fives,
        "open_fours": open_fours,
        "rush_fours": rush_fours,
        "live_threes": live_threes,
    }


def _choose_local_gobang_move(
    board: list[list[int]],
    ai_chess: int,
    player_chess: int,
) -> tuple[int, int]:
    threat = _find_best_threat(board, ai_chess, player_chess)
    if threat is not None:
        return threat[1], threat[2]
    center = 7
    if board[center][center] == 0:
        return center, center
    best_score = -1
    best = None
    for row in range(15):
        for col in range(15):
            if board[row][col] != 0 or not _has_neighbor(board, row, col):
                continue
            score = _score_point(board, row, col, ai_chess) * 2
            score += _score_point(board, row, col, player_chess)
            score += 20 - abs(row - center) - abs(col - center)
            if score > best_score:
                best_score = score
                best = (row, col)
    if best is not None:
        return best
    for row in range(15):
        for col in range(15):
            if board[row][col] == 0:
                return row, col
    raise ValueError("五子棋棋盘已满，无法生成落子")


def _count_direction(
    board: list[list[int]],
    chess: int,
    row: int,
    col: int,
    row_step: int,
    col_step: int,
) -> int:
    count = 0
    next_row = row + row_step
    next_col = col + col_step
    while 0 <= next_row < 15 and 0 <= next_col < 15 and board[next_row][next_col] == chess:
        count += 1
        next_row += row_step
        next_col += col_step
    return count


def _open_end(
    board: list[list[int]],
    chess: int,
    row: int,
    col: int,
    row_step: int,
    col_step: int,
    count: int,
) -> int:
    next_row = row + row_step * (count + 1)
    next_col = col + col_step * (count + 1)
    return 1 if 0 <= next_row < 15 and 0 <= next_col < 15 and board[next_row][next_col] == 0 else 0


def _has_neighbor(board: list[list[int]], row: int, col: int) -> bool:
    for row_step in range(-2, 3):
        for col_step in range(-2, 3):
            if row_step == 0 and col_step == 0:
                continue
            next_row = row + row_step
            next_col = col + col_step
            if 0 <= next_row < 15 and 0 <= next_col < 15 and board[next_row][next_col] != 0:
                return True
    return False


def _score_point(board: list[list[int]], row: int, col: int, chess: int) -> int:
    score = 0
    for row_step, col_step in ((1, 0), (0, 1), (1, 1), (1, -1)):
        forward = _count_direction(board, chess, row, col, row_step, col_step)
        backward = _count_direction(board, chess, row, col, -row_step, -col_step)
        length = forward + backward + 1
        open_ends = _open_end(board, chess, row, col, row_step, col_step, forward) + _open_end(
            board, chess, row, col, -row_step, -col_step, backward
        )
        if length >= 5:
            score += 100000
        elif length == 4 and open_ends == 2:
            score += 12000
        elif length == 4:
            score += 5000
        elif length == 3 and open_ends == 2:
            score += 1200
        elif length == 3:
            score += 360
        elif length == 2 and open_ends == 2:
            score += 120
        elif length == 2:
            score += 40
        elif open_ends > 0:
            score += 8
    return score
