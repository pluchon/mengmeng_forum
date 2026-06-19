"""AI Hub 业务编排."""
from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_image import dashscope_text_to_image
from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_images
from config import settings
from graphs.ai_write_graph import run_ai_write
from utils.image_mcp import enrich_image_prompt

logger = logging.getLogger(__name__)


class AiHubConfigError(RuntimeError):
    """AI Hub 外部供应商配置缺失."""


def generate_write_content(
    kind: str,
    messages: list[dict[str, str]],
) -> tuple[str, dict[str, Any]]:
    """执行 AI 写作模型分发."""
    return run_ai_write(kind, messages)


def generate_cover_hints(article: str) -> tuple[str, dict[str, Any]]:
    """根据正文生成封面图提示词."""
    ds = settings.deepseek
    system = (
        "你是论坛封面配图助手。根据用户正文提炼一个且仅一个「AI 绘图提示词」，"
        "必须严格使用以下单行模板（不要换行、不要列表、不要编号、不要引号包裹整句）：\n"
        "帮我画一张论坛帖子封面图，主题是【用不超过12字概括核心主题】，"
        "画面元素【用不超过20字描述1个主视觉，禁止并列多个无关主题】，"
        "风格【写实/插画/二次元/水彩四选一】，氛围【温馨/热血/治愈/悬疑四选一】。\n"
        "禁止输出第二套方案、禁止 markdown、禁止解释。"
    )
    messages: list[dict[str, str]] = [
        {"role": "system", "content": system},
        {"role": "user", "content": article[:12000]},
    ]
    base = ds.get("base_url") or "https://api.deepseek.com/v1"
    model = ds.get("model_flash") or "deepseek-v4-flash"
    key = ds.get("api_key") or ""
    return deepseek_chat_completion(base, key, model, messages)


def generate_gobang_move(
    board: list[list[int]],
    ai_chess: int = 2,
    model_code: str | None = None,
) -> dict[str, Any]:
    """调用 DeepSeek 生成五子棋 AI 落子，返回坐标、模型和策略信息。"""
    safe_board = _normalize_gobang_board(board)
    ds = settings.deepseek
    base = ds.get("base_url") or "https://api.deepseek.com/v1"
    model = _resolve_gobang_model(ds, model_code)
    key = ds.get("api_key") or ""
    player_chess = 1 if ai_chess == 2 else 2
    system = (
        "你是五子棋 AI。棋盘是 15x15，row/col 都从 0 开始。"
        "1 表示黑棋，2 表示白棋，0 表示空位。"
        "你必须为 AI 选择一个当前为空的合法落点。"
        "优先级：自己能五连则立即取胜；其次阻挡对手五连；再考虑活四、冲四、活三和中心势。"
        "只输出 JSON 对象，不要 markdown，不要解释，格式为 {\"row\":7,\"col\":7}。"
    )
    user = {
        "ai_chess": ai_chess,
        "player_chess": player_chess,
        "board": safe_board,
        "legend": {"0": "empty", "1": "black", "2": "white"},
    }
    usage: dict[str, Any] = {"model_code": model, "estimated": True}
    try:
        content, usage = deepseek_chat_completion(
            base,
            key,
            model,
            [
                {"role": "system", "content": system},
                {"role": "user", "content": json.dumps(user, ensure_ascii=False)},
            ],
        )
        row, col = _parse_gobang_point(content)
        if not _is_gobang_empty(safe_board, row, col):
            raise ValueError("DeepSeek 返回了非法五子棋坐标")
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "llm_with_rule_guard",
            "fallback": False,
            "usage": usage,
        }
    except Exception as exc:
        logger.warning("DeepSeek 五子棋不可用，使用 Python 本地策略兜底: %s", exc)
        row, col = _choose_local_gobang_move(safe_board, ai_chess, player_chess)
        return {
            "row": row,
            "col": col,
            "model": model,
            "model_name": model,
            "model_version": model,
            "strategy_name": "rule_based_fallback",
            "fallback": True,
            "usage": {**usage, "fallback_reason": str(exc)[:160]},
        }


def _resolve_gobang_model(ds: dict[str, Any], model_code: str | None) -> str:
    requested = (model_code or "").strip()
    flash = str(ds.get("model_flash") or "deepseek-v4-flash").strip()
    pro = str(ds.get("model_pro") or "deepseek-v4-pro").strip()
    if requested == pro:
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


def _parse_gobang_point(content: str) -> tuple[int, int]:
    text = (content or "").strip()
    match = re.search(r"\{[\s\S]*\}", text)
    if not match:
        raise ValueError("DeepSeek 未返回 JSON 坐标")
    data = json.loads(match.group(0))
    row = int(data.get("row"))
    col = int(data.get("col"))
    return row, col


def _is_gobang_empty(board: list[list[int]], row: int, col: int) -> bool:
    return 0 <= row < 15 and 0 <= col < 15 and board[row][col] == 0


def _find_tactical_gobang_move(
    board: list[list[int]],
    ai_chess: int,
    player_chess: int,
) -> tuple[int, int] | None:
    """优先处理一步取胜和一步必防，减少模型慢调用并提升棋力稳定性。"""
    win = _find_five_move(board, ai_chess)
    if win is not None:
        return win
    return _find_five_move(board, player_chess)


def _find_five_move(board: list[list[int]], chess: int) -> tuple[int, int] | None:
    for row in range(15):
        for col in range(15):
            if board[row][col] != 0:
                continue
            board[row][col] = chess
            five = _has_five(board, chess, row, col)
            board[row][col] = 0
            if five:
                return row, col
    return None


def _choose_local_gobang_move(
    board: list[list[int]],
    ai_chess: int,
    player_chess: int,
) -> tuple[int, int]:
    tactical = _find_tactical_gobang_move(board, ai_chess, player_chess)
    if tactical is not None:
        return tactical
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


def _has_five(board: list[list[int]], chess: int, row: int, col: int) -> bool:
    directions = ((1, 0), (0, 1), (1, 1), (1, -1))
    return any(
        _count_direction(board, chess, row, col, dr, dc)
        + _count_direction(board, chess, row, col, -dr, -dc)
        + 1
        >= 5
        for dr, dc in directions
    )


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
        if length >= 5:
            score += 100000
        elif length == 4:
            score += 6000
        elif length == 3:
            score += 900
        elif length == 2:
            score += 120
        else:
            score += 8
    return score


def generate_image(prompt: str, quality: str) -> tuple[str, dict[str, Any], bool]:
    """按质量档位生成图片，并返回是否使用 MCP 增强."""
    enhanced_prompt, mcp_used = enrich_image_prompt(prompt)
    if quality == "normal":
        url, usage = dashscope_text_to_image(enhanced_prompt)
        return url, usage, mcp_used

    hu = settings.huanapi
    base = str(hu.get("base_url") or "https://www.huanapi.com")
    img_key = str(hu.get("image_key") or "").strip()
    if not img_key:
        raise AiHubConfigError("GPT 生图未配置（HUANAPI_IMAGE_KEY）")

    premium_model = str(hu.get("model_image_premium") or "gpt-image-2").strip()
    if premium_model != "gpt-image-2":
        logger.warning(
            "huanapi.model_image_premium=%r，将使用官方模型名 gpt-image-2",
            premium_model,
        )
    url, usage = huanapi_images(base, img_key, "gpt-image-2", enhanced_prompt)
    return url, usage, mcp_used
