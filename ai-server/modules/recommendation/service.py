"""推荐模块的 LangChain 编排与结构化结果校验。"""

from __future__ import annotations

import asyncio
import hashlib
import json
from typing import Any

from langchain_core.prompts import ChatPromptTemplate

from clients.llm import text_llm
from config import settings
from runtime.ai_runtime import AiRuntime

_runtime = AiRuntime()
_FEATURE_VERSION = "v1"


class RecommendationFeatureService:
    """单步结构化提取；无需为无分支任务引入 LangGraph。"""

    async def article_feature(self, payload: dict[str, Any], trace_id: str) -> dict[str, Any]:
        article_id = _positive_id(payload.get("articleId"), "articleId")
        title = _text(payload.get("title"), "title", 200)
        content = _text(payload.get("content"), "content", 12000)
        board_name = _optional_text(payload.get("boardName"), 32)
        fallback = {
            "articleId": article_id,
            "featureVersion": _FEATURE_VERSION,
            "topics": _topics_from_names([board_name] if board_name else []),
            "summary": title[:120],
            "contentFingerprint": _fingerprint(title + "\n" + content),
            "generatedBy": "RULE_FALLBACK",
        }
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是论坛推荐特征提取器。只返回 JSON，不要 Markdown。topics 至多 5 项；每项为 {name, weight}，weight 在 0 到 1。第一项必须保留所属板块名称且原样输出。summary 不超过 80 字。"),
            ("human", "所属板块：{board_name}\n标题：{title}\n正文：{content}\n输出 JSON：{{\"topics\":[{{\"name\":\"\",\"weight\":0.0}}],\"summary\":\"\"}}"),
        ])
        raw = await self._invoke(prompt, {"board_name": board_name or "未分类", "title": title, "content": content}, trace_id, fallback)
        result = _normalize_feature(raw, fallback)
        result["articleId"] = article_id
        result["contentFingerprint"] = fallback["contentFingerprint"]
        return result

    async def user_profile(self, payload: dict[str, Any], trace_id: str) -> dict[str, Any]:
        explicit = _name_list(payload.get("explicitBoards"))
        recent7 = _signals(payload.get("recent7"))
        recent14 = _signals(payload.get("recent14"))
        negative_recent7 = _negative_signals(payload.get("negativeRecent7"))
        negative_recent14 = _negative_signals(payload.get("negativeRecent14"))
        allowed = explicit + [item["board"] for item in recent7] + [item["board"] for item in recent14]
        allowed_avoid = [item["board"] for item in negative_recent7] + [item["board"] for item in negative_recent14]
        fallback = {
            "featureVersion": _FEATURE_VERSION,
            "topics": _topics_from_names(allowed),
            "avoidTopics": _topics_from_names(allowed_avoid),
            "summary": "基于近期公开互动生成的内容偏好",
            "generatedBy": "RULE_FALLBACK",
        }
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是论坛用户画像整理器。只返回 JSON，不要 Markdown。topics.name 只能从正向输入板块中选择，avoidTopics.name 只能从负向输入板块中选择，均需原样输出；每组至多 5 项，weight 在 0 到 1。近 7 天权重高于第 8 至 14 天；手选兴趣优先。不得猜测敏感属性。"),
            ("human", "手选兴趣：{explicit}\n近7天正向聚合：{recent7}\n第8至14天正向聚合：{recent14}\n近7天负向反馈：{negative_recent7}\n第8至14天负向反馈：{negative_recent14}\n输出 JSON：{{\"topics\":[{{\"name\":\"\",\"weight\":0.0}}],\"avoidTopics\":[{{\"name\":\"\",\"weight\":0.0}}],\"summary\":\"\"}}"),
        ])
        raw = await self._invoke(prompt, {
            "explicit": explicit,
            "recent7": recent7,
            "recent14": recent14,
            "negative_recent7": negative_recent7,
            "negative_recent14": negative_recent14,
        }, trace_id, fallback)
        return _normalize_profile(raw, fallback, set(allowed), set(allowed_avoid))

    async def _invoke(
        self,
        prompt: ChatPromptTemplate,
        values: dict[str, Any],
        trace_id: str,
        fallback: dict[str, Any],
    ) -> dict[str, Any]:
        def invoke() -> dict[str, Any]:
            model_name = str(settings.dashscope.get("model_text_flash") or "qwen3.6-flash")
            response = _runtime.call_llm(
                lambda: (prompt | text_llm(temperature=0.1, model_name=model_name)).invoke(values),
                trace_id=trace_id,
                model_name=model_name,
                retries=1,
                fallback=lambda: fallback,
                fallback_model_name="rules-fallback",
            )
            if isinstance(response, dict):
                return response
            content = getattr(response, "content", response)
            return _json_object(content)

        return await asyncio.to_thread(invoke)


def _normalize_feature(raw: dict[str, Any], fallback: dict[str, Any]) -> dict[str, Any]:
    result = dict(fallback)
    used_fallback = raw is fallback
    result["topics"] = _normalize_topics(raw.get("topics"), None) or fallback["topics"]
    result["summary"] = _optional_text(raw.get("summary"), 80) or fallback["summary"]
    result["generatedBy"] = "RULE_FALLBACK" if used_fallback else "AI"
    return result


def _normalize_profile(
    raw: dict[str, Any],
    fallback: dict[str, Any],
    allowed: set[str],
    allowed_avoid: set[str],
) -> dict[str, Any]:
    result = dict(fallback)
    used_fallback = raw is fallback
    result["topics"] = _normalize_topics(raw.get("topics"), allowed) or fallback["topics"]
    result["avoidTopics"] = _normalize_topics(raw.get("avoidTopics"), allowed_avoid) or fallback["avoidTopics"]
    result["summary"] = _optional_text(raw.get("summary"), 80) or fallback["summary"]
    result["generatedBy"] = "RULE_FALLBACK" if used_fallback else "AI"
    return result


def _json_object(value: Any) -> dict[str, Any]:
    text = str(value or "").strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[-1].rsplit("```", 1)[0].strip()
    try:
        parsed = json.loads(text)
    except (TypeError, ValueError):
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _normalize_topics(raw: Any, allowed: set[str] | None) -> list[dict[str, Any]]:
    if not isinstance(raw, list):
        return []
    result: list[dict[str, Any]] = []
    for item in raw[:5]:
        if not isinstance(item, dict):
            continue
        name = _optional_text(item.get("name"), 32)
        if not name or (allowed is not None and name not in allowed):
            continue
        try:
            weight = float(item.get("weight", 0))
        except (TypeError, ValueError):
            continue
        if weight <= 0:
            continue
        result.append({"name": name, "weight": min(weight, 1.0)})
    return result


def _topics_from_names(names: list[str]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, name in enumerate(dict.fromkeys(item for item in names if item)):
        if index >= 5:
            break
        result.append({"name": name, "weight": round(max(0.4, 1.0 - index * 0.15), 2)})
    return result


def _signals(raw: Any) -> list[dict[str, Any]]:
    if not isinstance(raw, list):
        return []
    result: list[dict[str, Any]] = []
    for item in raw[:8]:
        if not isinstance(item, dict):
            continue
        board = _optional_text(item.get("board"), 32)
        score = item.get("score")
        if board and isinstance(score, (int, float)):
            result.append({"board": board, "score": round(float(score), 2)})
    return result


def _negative_signals(raw: Any) -> list[dict[str, Any]]:
    if not isinstance(raw, list):
        return []
    result: list[dict[str, Any]] = []
    for item in raw[:8]:
        if not isinstance(item, dict):
            continue
        board = _optional_text(item.get("board"), 32)
        score = item.get("score")
        reasons = item.get("reasons")
        if not board or not isinstance(score, (int, float)):
            continue
        normalized_reasons = []
        if isinstance(reasons, list):
            normalized_reasons = [_optional_text(reason, 200) for reason in reasons[:3]]
            normalized_reasons = [reason for reason in normalized_reasons if reason]
        result.append({
            "board": board,
            "score": round(float(score), 2),
            "reasons": normalized_reasons,
        })
    return result


def _name_list(raw: Any) -> list[str]:
    if not isinstance(raw, list):
        return []
    return [name for item in raw if (name := _optional_text(item, 32))]


def _positive_id(value: Any, field: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"{field} 必须是正整数")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{field} 必须是正整数") from exc
    if parsed <= 0:
        raise ValueError(f"{field} 必须是正整数")
    return parsed


def _text(value: Any, field: str, max_length: int) -> str:
    text = _optional_text(value, max_length)
    if not text:
        raise ValueError(f"{field} 不能为空")
    return text


def _optional_text(value: Any, max_length: int) -> str:
    return str(value or "").strip()[:max_length]


def _fingerprint(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()
