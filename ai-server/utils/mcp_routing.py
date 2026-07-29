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
_EXPLICIT_WEB_IMAGE_REQUEST_RE = re.compile(
    r"(?:联网|网络|网上|互联网|网页|百科).{0,24}(?:图片|图集|配图|照片)|"
    r"(?:搜|搜索|找|抓取).{0,24}(?:图片|图集|配图|照片)|"
    r"(?:展示|显示|看看).{0,20}(?:图片|图集|配图|照片)",
    re.I,
)


def is_explicit_web_image_request(message: str) -> bool:
    """识别用户明确要求联网检索并展示图片的请求。"""
    return bool(_EXPLICIT_WEB_IMAGE_REQUEST_RE.search((message or "").strip()))


def _mcp_enabled() -> bool:
    mcp = settings.raw.get("mcp", {}) or {}
    if not mcp.get("enabled", True):
        return False
    tav = settings.tavily
    if not tav.get("enabled", True):
        return False
    return bool((tav.get("api_key") or "").strip())


def is_tavily_search_enabled() -> bool:
    """供看板娘编排层判断联网检索是否可用。"""
    return _mcp_enabled()


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
) -> tuple[bool, str]:
    """用轻量模型判断是否需要联网及搜索词."""
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
            "你是写作助手的路由器。用户要写论坛相关内容。"
            "若问题纯属本站功能/规则/积分/VIP/发帖流程，输出 need_search=false。"
            "若涉及外部事实、新闻、技术细节、人物生平、你不确定的内容，输出 need_search=true 并给出简洁 search query。"
            '只输出 JSON：{"need_search":bool,"query":"..."}'
        )
    user = f"skill={skill}\n用户消息：{message[:1500]}"
    try:
        raw, _ = dashscope_chat_completion(
            model,
            [{"role": "system", "content": sys}, {"role": "user", "content": user}],
            temperature=0.1,
        )
    except Exception:
        logger.exception("MCP 路由 LLM 失败")
        return False, ""
    data = _parse_router_json(raw)
    if not isinstance(data, dict):
        return False, ""
    need = bool(data.get("need_search"))
    query = str(data.get("query") or message).strip()[:300]
    return need, query


def assess_mcp_for_writing(message: str) -> tuple[bool, str, str]:
    """
    返回 (need_search, search_query, local_kb_snippet).
    local_kb_snippet 非空时注入系统提示，且不搜索。
    """
    if not _mcp_enabled():
        return False, "", ""
    skill = "writing"
    covered, snippet = local_kb_covers_writing(message)
    if covered:
        return False, "", snippet
    need, query = _llm_route_decision(message=message, skill=skill, mode="writing")
    return need, query, ""


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
    need, query = _llm_route_decision(message=p, skill="drawing", mode="image")
    return need, query or p
