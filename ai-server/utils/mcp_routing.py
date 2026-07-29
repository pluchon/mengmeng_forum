"""
判断是否需要调用 MCP（Tavily）联网搜索。
站点帮助 skill 永不搜索；写作 / 生图按需触发。
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings
from utils.rag_enhance import keyword_overlap_score
from utils.site_help import get_site_help_snippet

logger = logging.getLogger(__name__)

_FORUM_INTERNAL_RE = re.compile(
    r"发帖|回帖|评论|积分|签到|VIP|会员|版规|抽奖|表情包|私信|"
    r"账号|注册|登录|萌萌技术分享笔记|论坛|板块|版主|管理员",
    re.I,
)
def _mcp_enabled() -> bool:
    mcp = settings.raw.get("mcp", {}) or {}
    if not mcp.get("enabled", True):
        return False
    tav = settings.tavily
    if not tav.get("enabled", True):
        return False
    return bool((tav.get("api_key") or "").strip())


def local_kb_covers_writing(message: str) -> tuple[bool, str]:
    """
    写作模式：若用户问题可由站点帮助文档回答，则视为本地知识库命中，不联网。
    """
    corpus = get_site_help_snippet()
    if not corpus:
        return False, ""
    score = keyword_overlap_score(message, corpus)
    if score >= 0.28 or _FORUM_INTERNAL_RE.search(message):
        return True, corpus[:2500]
    return False, ""


def _parse_router_json(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def _llm_route_decision(
    *,
    message: str,
    skill: str,
    mode: str,
    history: list[dict[str, str]] | None = None,
) -> tuple[bool, str, bool]:
    """用 Flash 模型生成联网检索计划。"""
    ds = settings.dashscope
    model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
    if mode == "image":
        sys = (
            "你是生图助手的检索路由器。判断用户描述是否包含你不确定具体外观/含义的主体"
            "（冷门人物、新梗、特定建筑/产品型号、生僻概念等）。"
            "若需要联网查资料才能画准，输出 JSON："
            '{"need_search":true,"query":"英文或中文搜索词"}；'
            "若描述已足够具体（如「一只橘猫」「夕阳下的海边」），输出 "
            '{"need_search":false,"query":""}。只输出 JSON。'
        )
    else:
        sys = (
            "你是看板娘的联网检索规划器。理解用户本轮意图与最近话题后，决定是否需要联网。"
            "站点功能、规则、积分、VIP、发帖流程等可由本站知识回答时不联网；"
            "外部事实、人物、地点、作品、新闻、技术细节或用户明确要求联网搜索时，可联网。"
            "need_search_images=true 仅表示应把联网图片作为本条消息的图集展示："
            "用户要求看图片，或图片确实有助于理解实体、地点、作品时为 true；否则为 false。"
            "need_search_images=true 时 need_search 必须为 true。query 是结合本轮与已有话题的简洁检索词。"
            '只输出 JSON：{"need_search":bool,"need_search_images":bool,"query":"..."}'
        )
    recent_history = "\n".join(
        f"{item.get('role', '')}: {str(item.get('content') or '')[:300]}"
        for item in (history or [])[-6:]
        if isinstance(item, dict)
    )
    user = f"skill={skill}\n最近对话：\n{recent_history or '（无）'}\n用户消息：{message[:1500]}"
    try:
        raw, _ = dashscope_chat_completion(
            model,
            [{"role": "system", "content": sys}, {"role": "user", "content": user}],
            temperature=0.0,
        )
    except Exception:
        logger.exception("MCP 路由 LLM 失败")
        return False, "", False
    data = _parse_router_json(raw)
    if not isinstance(data, dict):
        return False, "", False
    need = bool(data.get("need_search"))
    need_images = bool(data.get("need_search_images"))
    if need_images:
        need = True
    query = str(data.get("query") or message).strip()[:300]
    return need, query, need_images


def assess_mcp_for_writing(
    message: str,
    history: list[dict[str, str]] | None = None,
) -> tuple[bool, str, str, bool]:
    """
    返回 (need_search, search_query, local_kb_snippet, need_search_images).
    local_kb_snippet 非空时注入系统提示，且不搜索。
    """
    if not _mcp_enabled():
        return False, "", "", False
    skill = "writing"
    covered, snippet = local_kb_covers_writing(message)
    if covered:
        return False, "", snippet, False
    need, query, need_images = _llm_route_decision(
        message=message,
        skill=skill,
        mode="writing",
        history=history,
    )
    return need, query, "", need_images


def assess_mcp_for_image(prompt: str) -> tuple[bool, str]:
    """生图：不确定画什么时联网查视觉描述."""
    if not _mcp_enabled():
        return False, ""
    p = (prompt or "").strip()
    if len(p) < 4:
        return False, ""
    # 过于简单/generic 的描述通常不需要搜索
    if len(p) <= 12 and not re.search(r"[A-Za-z]{4,}|[\u4e00-\u9fa5]{6,}", p):
        return False, ""
    need, query, _ = _llm_route_decision(message=p, skill="drawing", mode="image")
    return need, query or p
