"""看板娘出行意图：规则检测 + 自动组装百度地图工具调用."""

from __future__ import annotations

import re
from typing import Any

_AFFIRM_RE = re.compile(
    r"^(是的?|对|嗯|好|可以|想去|要去|走|行|没问题|ok|yes|想去看看|想去看看)[\s!！。~]*$",
    re.I,
)
_WANT_GO_RE = re.compile(
    r"想去|要去|去看看|打算去|准备去|走一趟|出发去|到.{1,10}(?:去|玩|看看)|"
    r"自驾|开车去|几个人|一同去",
    re.I,
)
_TRAVEL_TOPIC_RE = re.compile(
    r"雪山|川西|西藏|新疆|云南|旅行|旅游|自驾|路线|出发|想去|行程|攻略|"
    r"景点|度假|徒步|露营",
    re.I,
)
_ORIGIN_RE = re.compile(
    r"(?:从|自)([\u4e00-\u9fa5]{2,12}(?:市|省|区|县|州)?)|"
    r"出发(?:地|城市)?[是为：:]?\s*([\u4e00-\u9fa5]{2,12})",
)
_DEST_RE = re.compile(
    r"(?:去|到|前往)([\u4e00-\u9fa5]{2,12}(?:雪山|景区|市|省|区|县|州)?)|"
    r"目的地[是为：:]?\s*([\u4e00-\u9fa5]{2,12})",
)
_MODE_DRIVE_RE = re.compile(r"开车|驾车|自驾|小汽车|轿车", re.I)
_MODE_TRANSIT_RE = re.compile(r"公交|地铁|公共交通", re.I)
_MODE_WALK_RE = re.compile(r"步行|走路", re.I)
_MODE_RIDE_RE = re.compile(r"骑行|骑车|自行车", re.I)
_PEOPLE_RE = re.compile(r"(\d+)\s*人|一共\s*(\d+)|我们\s*(\d+)", re.I)
_DATE_RE = re.compile(r"(\d{1,2}月\d{1,2}日|下周|明天|后天|周末|国庆|春节)", re.I)


def _pick_group(m: re.Match | None, *groups: int) -> str:
    if not m:
        return ""
    for g in groups:
        try:
            v = m.group(g)
        except IndexError:
            continue
        if v:
            return v.strip()
    return ""


def _extract_trip_fields(message: str, history: list[dict[str, str]]) -> dict[str, str]:
    blob = message
    for item in (history or [])[-8:]:
        blob += "\n" + (item.get("content") or "")
    origin = _pick_group(_ORIGIN_RE.search(blob), 1, 2)
    dest = _pick_group(_DEST_RE.search(blob), 1, 2)
    mode = "driving"
    if _MODE_TRANSIT_RE.search(blob):
        mode = "transit"
    elif _MODE_WALK_RE.search(blob):
        mode = "walking"
    elif _MODE_RIDE_RE.search(blob):
        mode = "riding"
    elif _MODE_DRIVE_RE.search(blob):
        mode = "driving"
    people = ""
    pm = _PEOPLE_RE.search(blob)
    if pm:
        for g in range(1, pm.lastindex + 1 if pm.lastindex else 0):
            try:
                if pm.group(g):
                    people = pm.group(g)
                    break
            except IndexError:
                pass
    date_hint = _pick_group(_DATE_RE.search(blob), 1)
    return {
        "origin": origin,
        "destination": dest,
        "mode": mode,
        "people": people,
        "date_hint": date_hint,
    }


def _missing_fields(fields: dict[str, str]) -> list[str]:
    missing: list[str] = []
    if not fields.get("origin"):
        missing.append("出发地")
    if not fields.get("destination"):
        missing.append("目的地")
    if not fields.get("people"):
        missing.append("出行人数")
    if not re.search(r"driving|transit|walking|riding", fields.get("mode", "")):
        missing.append("出行方式（是否开车/公交等）")
    else:
        # 始终确认方式（用户可能未明说）
        blob_mode = fields.get("mode", "driving")
        if blob_mode == "driving" and "出行方式" not in missing:
            pass
    if not fields.get("date_hint"):
        missing.append("计划出发日期")
    # 若未识别方式，单独问
    if fields.get("mode") == "driving" and not _MODE_DRIVE_RE.search(
        fields.get("_blob", "")
    ):
        if "出行方式" not in missing and not fields.get("people"):
            missing.append("出行方式（驾车/公交/步行/骑行）")
    return missing[:5]


def rule_travel_plan(message: str, history: list[dict[str, str]] | None) -> dict[str, Any]:
    """
    规则层出行阶段，优先于 LLM 调度，减少漏判。
    返回 phase, missing_fields, tool_calls, destination_hint
    """
    msg = (message or "").strip()
    hist = history or []
    assistant_prev = " ".join(
        (h.get("content") or "") for h in hist[-6:] if h.get("role") == "assistant"
    )
    user_prev = " ".join(
        (h.get("content") or "") for h in hist[-6:] if h.get("role") == "user"
    )
    fields = _extract_trip_fields(msg, hist)
    fields["_blob"] = msg + user_prev

    # 用户明确愿前往 / 肯定助手邀请
    want_go = bool(_WANT_GO_RE.search(msg))
    affirm = bool(_AFFIRM_RE.match(msg)) and _TRAVEL_TOPIC_RE.search(assistant_prev + user_prev)
    scenic_only = _TRAVEL_TOPIC_RE.search(msg) and not want_go and not affirm and not fields["origin"]

    if scenic_only and not fields["destination"]:
        dest_hint = ""
        dm = re.search(r"([\u4e00-\u9fa5]{2,8}(?:雪山|川西|西藏|新疆|云南|景区))", msg)
        if dm:
            dest_hint = dm.group(1)
        return {
            "phase": "inspire",
            "missing_fields": [],
            "tool_calls": [],
            "destination_hint": dest_hint,
        }

    if want_go or affirm or (fields["origin"] and fields["destination"]):
        missing = []
        if not fields["origin"]:
            missing.append("出发地")
        if not fields["destination"]:
            missing.append("目的地")
        if not fields["people"]:
            missing.append("出行人数")
        if not _MODE_DRIVE_RE.search(msg + user_prev) and not _MODE_TRANSIT_RE.search(
            msg + user_prev
        ) and not _MODE_WALK_RE.search(msg + user_prev):
            missing.append("出行方式（是否驾车、公交、步行或骑行）")
        if not fields["date_hint"]:
            missing.append("计划出发日期")

        if missing:
            return {
                "phase": "collect",
                "missing_fields": missing,
                "tool_calls": [],
                "destination_hint": fields.get("destination") or "",
            }

        origin = fields["origin"]
        dest = fields["destination"]
        mode = fields.get("mode") or "driving"
        tool_calls: list[dict[str, Any]] = [
            {
                "tool": "map_directions",
                "arguments": {
                    "origin": origin,
                    "destination": dest,
                    "mode": mode,
                },
            },
            {
                "tool": "map_weather",
                "arguments": {"location": dest},
            },
        ]
        return {
            "phase": "plan",
            "missing_fields": [],
            "tool_calls": tool_calls,
            "destination_hint": dest,
        }

    return {"phase": "none", "missing_fields": [], "tool_calls": [], "destination_hint": ""}


def merge_travel_plans(rule: dict[str, Any], llm: dict[str, Any]) -> dict[str, Any]:
    """规则优先：collect/plan/inspire 不被 LLM 的 none 覆盖。"""
    r_phase = str(rule.get("phase") or "none")
    l_phase = str(llm.get("phase") or "none")
    if r_phase != "none":
        out = dict(rule)
        if r_phase == "plan" and not out.get("tool_calls") and llm.get("tool_calls"):
            out["tool_calls"] = llm.get("tool_calls")
        if not out.get("destination_hint") and llm.get("destination_hint"):
            out["destination_hint"] = llm.get("destination_hint")
        return out
    if l_phase != "none":
        return llm
    return rule
