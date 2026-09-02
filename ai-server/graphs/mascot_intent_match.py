"""看板娘牵线：判断两条意愿能不能互相帮上。

这里刻意保持极简——一次调用判一批，不建图、不接工具、不碰任何身份信息。
输入只有两段脱敏文本，输出只有「配不配 + 一句交集描述」。身份、可见性、
要不要通知，全在 Java。

判定宁严勿宽：牵错线比不牵线更伤人。用户收到一条莫名其妙的「有人和你想的一样」
只会觉得被打扰，而漏掉一次匹配他根本不会知道。
"""
from __future__ import annotations

import json
import logging
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from config import settings
from utils.json_parse import parse_json_object

logger = logging.getLogger(__name__)

# 只要一小段 JSON，封顶避免异常时一路生成
_MATCH_MAX_TOKENS = 800

_SYSTEM = """你在给一个论坛做「牵线」判定。下面每一对是两个人分别说过的需求或能力，
判断他们**互相认识一下是否真的有用**。

判定标准（宁严勿宽）：
- 只有当一方想知道/想做的事，另一方确实能提供或愿意一起做，才算配上。
- 话题沾边不算。都提到「旅行」不算；一个想问川西路况、另一个刚走完川西，才算。
- 拿不准就判不配。牵错线比不牵线更伤人——收到一条莫名其妙的邀约只会让人觉得被打扰。

配上时写一句 reason：
- 用第三人称、不超过 30 字，说清**交集是什么**，例如「都在关注川西自驾的具体路况」。
- 这句话会原样发给双方，所以绝不能出现任何指向具体某个人的说法，
  不要写「他」「对方是」「某某」，也不要复述任何一方的原话细节。

只输出 JSON：{"results":[{"key":"...","match":true,"reason":"..."}]}
每个 key 都要出现在结果里；不配的写 match=false、reason 留空。"""


def match_intent_pairs(pairs: list[dict[str, str]]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """判定一批意愿对。返回 (结果列表, 用量)。"""
    lines = []
    for item in pairs:
        lines.append(f'- key={item["key"]}\n  甲：{item["a"]}\n  乙：{item["b"]}')
    user = "请逐对判定：\n" + "\n".join(lines)
    model = str(settings.dashscope.get("model_text_flash") or "qwen3.7-flash")
    try:
        raw, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": _SYSTEM}, {"role": "user", "content": user}],
            temperature=0.0,
            max_tokens=_MATCH_MAX_TOKENS,
            response_format={"type": "json_object"},
        )
    except Exception:
        # 判定失败就当作这一批都没配上：牵线是锦上添花，不该因为它出错影响别的事
        logger.exception("牵线判定调用失败，本批全部按不匹配处理")
        return [{"key": item["key"], "match": False, "reason": ""} for item in pairs], {}

    data = parse_json_object(raw)
    wanted = {item["key"] for item in pairs}
    out: dict[str, dict[str, Any]] = {}
    if isinstance(data, dict) and isinstance(data.get("results"), list):
        for row in data["results"]:
            if not isinstance(row, dict):
                continue
            key = str(row.get("key") or "").strip()
            if key not in wanted:
                continue
            matched = bool(row.get("match"))
            reason = str(row.get("reason") or "").replace("\n", " ").strip()[:60]
            # 说配上了却说不出交集，等于没判——按不配处理
            if matched and len(reason) < 4:
                matched = False
                reason = ""
            out[key] = {"key": key, "match": matched, "reason": reason if matched else ""}
    # 模型漏掉的一律按不配，别让缺失变成默认通过
    for item in pairs:
        out.setdefault(item["key"], {"key": item["key"], "match": False, "reason": ""})
    return [out[item["key"]] for item in pairs], usage
