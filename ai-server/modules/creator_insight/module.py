"""使用 Flash 模型生成温和、精炼的创作者数据小结。"""

from __future__ import annotations

import asyncio
import json
from typing import Any

from langchain_core.prompts import ChatPromptTemplate

from clients.llm import text_llm
from config import settings
from runtime.ai_runtime import AiRuntime
from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult
from modules.creation.usage import aggregate_usage

_runtime = AiRuntime()
_FIELDS = (
    "readCount",
    "previousReadCount",
    "likeCount",
    "previousLikeCount",
    "workCount",
    "previousWorkCount",
    "newFollowerCount",
    "previousNewFollowerCount",
    "totalFollowerCount",
)


class CreatorInsightModule:
    """单步结构化生成，无需为无分支任务引入多代理。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        payload = _normalize_payload(request.payload)
        data, usage = await asyncio.to_thread(_generate, payload, request.trace_id)
        return ModuleResult(success=True, data=data, usage=usage)


def _generate(payload: dict[str, Any], trace_id: str) -> tuple[dict[str, Any], dict[str, Any]]:
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "你是年轻、真诚的创作陪伴助手。你只能依据输入的统计数字写小结，不得编造原因、受众偏好或不存在的数据。"
            "表达要像熟悉创作者的朋友，温和、有活力并说人话；不要使用首先、其次、综上所述等结构词。"
            "只写小结与数据亮点，不给行动建议。只返回JSON对象：headline不超过14字，overview不超过80字，"
            "highlights为1到3条字符串，每条不超过32字。避免每次使用相同句式，但任何判断都必须能由数字直接支持。"
            "数据很少或全为0时如实说明仍在积累。",
        ),
        (
            "human",
            "统计周期：{periodLabel}（{startDate} 至 {endDate}）\n"
            "本期阅读 {readCount}，上期 {previousReadCount}\n"
            "本期点赞 {likeCount}，上期 {previousLikeCount}\n"
            "本期发布 {workCount}，上期 {previousWorkCount}\n"
            "本期新增粉丝 {newFollowerCount}，上期 {previousNewFollowerCount}\n"
            "当前粉丝总数 {totalFollowerCount}\n"
            "输出JSON：{{\"headline\":\"\",\"overview\":\"\",\"highlights\":[\"\"]}}",
        ),
    ])
    flash_model = str(settings.dashscope.get("model_text_flash") or "qwen3.7-flash")
    deep_model = str(settings.dashscope.get("model_text_deep") or "qwen3.7-max")
    model_name = deep_model if _needs_deep(payload) else flash_model

    def invoke(target_model: str) -> Any:
        return (prompt | text_llm(temperature=0.2, model_name=target_model)).invoke(payload)

    response = _runtime.call_llm(
        lambda: invoke(model_name),
        trace_id=trace_id,
        model_name=model_name,
        retries=0,
    )
    responses = [(response, model_name)]
    try:
        result = _parse_result(response)
    except ModuleRequestError:
        if model_name == deep_model:
            raise
        response = _runtime.call_llm(
            lambda: invoke(deep_model),
            trace_id=trace_id,
            model_name=deep_model,
            retries=0,
        )
        responses.append((response, deep_model))
        result = _parse_result(response)
    usage = aggregate_usage([
        _usage_item(item, used_model, index)
        for index, (item, used_model) in enumerate(responses)
    ])
    return result, usage


def _parse_result(response: Any) -> dict[str, Any]:
    raw = _json_object(getattr(response, "content", response))
    result = {
        "headline": _text(raw.get("headline"), 14),
        "overview": _text(raw.get("overview"), 70),
        "highlights": _highlights(raw.get("highlights")),
    }
    result["highlight"] = result["highlights"][0] if result["highlights"] else ""
    if not result["headline"] or not result["overview"] or not result["highlights"]:
        raise ModuleRequestError("CREATOR_INSIGHT_INVALID_RESULT", "AI 数据小结结果不完整")
    return result


def _usage_item(response: Any, model_name: str, index: int) -> dict[str, Any]:
    usage_meta = getattr(response, "usage_metadata", None) or {}
    return {
        "stage": f"creator_insight_{index + 1}",
        "model_code": model_name,
        "input_tokens": usage_meta.get("input_tokens", 0),
        "output_tokens": usage_meta.get("output_tokens", 0),
        "estimated": not bool(usage_meta),
    }


def _needs_deep(payload: dict[str, Any]) -> bool:
    deltas = [
        payload["readCount"] - payload["previousReadCount"],
        payload["likeCount"] - payload["previousLikeCount"],
        payload["workCount"] - payload["previousWorkCount"],
        payload["newFollowerCount"] - payload["previousNewFollowerCount"],
    ]
    active = [value for value in deltas if value != 0]
    return len(active) >= 3 and any(value > 0 for value in active) and any(value < 0 for value in active)


def _normalize_payload(payload: dict[str, Any]) -> dict[str, Any]:
    period_label = _text(payload.get("periodLabel"), 16)
    start_date = _text(payload.get("startDate"), 10)
    end_date = _text(payload.get("endDate"), 10)
    if not period_label or not start_date or not end_date:
        raise ModuleRequestError("INVALID_CREATOR_INSIGHT_PAYLOAD", "统计周期不完整")
    result: dict[str, Any] = {
        "periodLabel": period_label,
        "startDate": start_date,
        "endDate": end_date,
    }
    for field in _FIELDS:
        try:
            result[field] = max(0, int(payload.get(field) or 0))
        except (TypeError, ValueError) as exc:
            raise ModuleRequestError("INVALID_CREATOR_INSIGHT_PAYLOAD", f"{field} 必须是非负整数") from exc
    return result


def _json_object(value: Any) -> dict[str, Any]:
    text = str(value or "").strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[-1].rsplit("```", 1)[0].strip()
    try:
        parsed = json.loads(text)
    except (TypeError, ValueError) as exc:
        raise ModuleRequestError("CREATOR_INSIGHT_INVALID_RESULT", "AI 数据小结不是合法JSON") from exc
    if not isinstance(parsed, dict):
        raise ModuleRequestError("CREATOR_INSIGHT_INVALID_RESULT", "AI 数据小结格式错误")
    return parsed


def _text(value: Any, limit: int) -> str:
    return str(value or "").strip()[:limit]


def _highlights(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [text for item in value if (text := _text(item, 32))][:3]
