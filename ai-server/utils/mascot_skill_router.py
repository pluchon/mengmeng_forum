"""
看板娘 skill=chat 时，将用户意图路由为 writing（代笔/联网/MCP）或 help（站点帮助）。
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings
from utils.mcp_routing import _FORUM_INTERNAL_RE, is_explicit_web_image_request

_TRAVEL_SCENE_RE = re.compile(
    r"雪山|川西|西藏|新疆|云南|旅行|旅游|自驾|路线|攻略|景点|想去|去看看|"
    r"出发|行程|度假|徒步|露营|天气怎么样",
    re.I,
)

logger = logging.getLogger(__name__)

_HELP_STRONG_RE = re.compile(
    r"(怎么|如何|怎样|在哪|哪里|能不能|可以吗).{0,12}(发帖|回帖|评论|积分|签到|VIP|会员|"
    r"版规|抽奖|私信|注册|登录|密码|审核|版主|板块|论坛|萌萌技术分享笔记|"
    r"陪伴助手|看板娘|消息中心|未读|公告)",
    re.I,
)
_WRITING_STRONG_RE = re.compile(
    r"帮我写|代写|起草|润色|改写|列提纲|写一段|写篇|发帖草稿|论坛帖|文案|"
    r"扩写|缩写|翻译成|标题怎么写",
    re.I,
)
_HELP_WEAK_RE = re.compile(
    r"^(请问|想问|咨询).{0,8}(论坛|本站|网站|功能|规则)",
    re.I,
)


def _parse_json(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def _heuristic_route(message: str) -> str | None:
    msg = (message or "").strip()
    if not msg:
        return "help"
    if is_explicit_web_image_request(msg):
        return "writing"
    if _TRAVEL_SCENE_RE.search(msg):
        return "writing"
    if _WRITING_STRONG_RE.search(msg):
        return "writing"
    if _HELP_STRONG_RE.search(msg) or _HELP_WEAK_RE.search(msg):
        return "help"
    if _FORUM_INTERNAL_RE.search(msg) and len(msg) < 120:
        if not re.search(r"雪山|旅行|旅游|风景|攻略|路线|四川|西藏|云南", msg):
            return "help"
    return None


def _llm_route(message: str, history: list[dict[str, str]]) -> str:
    ds = settings.dashscope
    model = str(ds.get("model_text_flash") or ds.get("model_text") or "qwen3.6-flash")
    hist_txt = ""
    for item in (history or [])[-4:]:
        role = item.get("role", "")
        content = (item.get("content") or "")[:300]
        hist_txt += f"{role}: {content}\n"
    sys = (
        "你是论坛看板娘的意图路由器。判断用户本轮更需要哪种能力：\n"
        "- help：询问本站功能、规则、积分、VIP、发帖流程、消息、审核等使用问题；"
        "回答应简短条目化，不代写长文，不联网。\n"
        "- writing：代写/润色论坛帖文、查外部事实、旅行闲聊与路线、需结合联网或地图的场景。\n"
        '只输出 JSON：{"intent":"help|writing"}'
    )
    user = f"近期对话：\n{hist_txt}\n本轮用户：{message[:800]}"
    try:
        raw, _ = dashscope_chat_completion(
            model,
            [{"role": "system", "content": sys}, {"role": "user", "content": user}],
            temperature=0.05,
        )
    except Exception:
        logger.exception("看板娘意图路由 LLM 失败")
        return "writing"
    data = _parse_json(raw)
    if not isinstance(data, dict):
        return "writing"
    intent = str(data.get("intent") or "").strip().lower()
    return "help" if intent == "help" else "writing"


def route_mascot_skill(message: str, history: list[dict[str, str]] | None) -> str:
    """返回 writing 或 help。"""
    hit = _heuristic_route(message)
    if hit:
        return hit
    return _llm_route(message, history or [])
