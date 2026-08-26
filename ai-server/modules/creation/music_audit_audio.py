"""用户上传歌曲的音频侧审核与曲风分析。"""

from __future__ import annotations

import logging
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator

from clients.dashscope_audio_client import dashscope_audio_completion
from config import settings
from modules.creation.music_audit_parse import parse_audit_model
from modules.creation.usage import usage_item

logger = logging.getLogger(__name__)

RiskLevel = Literal["low", "medium", "high"]
EnergyLevel = Literal["low", "medium", "high", ""]


class MusicAudioAuditResult(BaseModel):
    moodTags: list[str] = Field(default_factory=list, max_length=8)
    genre: str = Field(default="", max_length=40)
    energy: EnergyLevel = ""
    vocal: bool = False
    safe: bool = True
    risk: RiskLevel = "low"
    reasons: list[str] = Field(default_factory=list, max_length=8)

    @field_validator("reasons", mode="before")
    @classmethod
    def normalize_reasons(cls, value: Any) -> list[str]:
        if isinstance(value, str):
            text = value.strip()
            return [text[:120]] if text else []
        if not isinstance(value, list):
            return []
        result: list[str] = []
        for item in value:
            text = str(item or "").strip()[:120]
            if text and text not in result:
                result.append(text)
        return result[:8]

    @field_validator("moodTags", mode="before")
    @classmethod
    def normalize_moods(cls, value: Any) -> list[str]:
        if isinstance(value, str):
            text = value.strip()[:16]
            return [text] if text else []
        if not isinstance(value, list):
            return []
        result: list[str] = []
        for item in value:
            tag = str(item or "").strip()[:16]
            if tag and tag not in result:
                result.append(tag)
        return result[:8]

    @field_validator("risk", mode="before")
    @classmethod
    def normalize_risk(cls, value: Any) -> str:
        text = str(value or "low").strip().lower()
        if text in {"low", "medium", "high"}:
            return text
        return "low"

    @field_validator("energy", mode="before")
    @classmethod
    def normalize_energy(cls, value: Any) -> str:
        text = str(value or "").strip().lower()
        if text in {"low", "medium", "high"}:
            return text
        return ""


_AUDIO_PROMPT = (
    "你是论坛用户上传歌曲的音频审核与曲风分析节点。请听这段音频并输出 JSON："
    "moodTags（最多8个中文氛围标签）、genre（曲风/流派）、energy（low/medium/high）、"
    "vocal（是否有人声）、safe（是否适合公开展示）、risk（low/medium/high）、reasons（字符串数组）。"
    "若音频含明显色情、暴力、违法或极端不适合社区的内容，safe=false 且 risk=high。"
    "只输出合法 JSON，不要 Markdown。"
)


def audit_music_audio(audio_url: str) -> tuple[dict[str, Any], dict[str, Any]]:
    """调用 DashScope Omni 分析音频 URL，返回结构化结果与用量。"""
    url = audio_url.strip()
    if not url:
        raise ValueError("audio_url 不能为空")
    try:
        model = str(settings.dashscope.get("model_omni") or "qwen3-omni-flash")
        logger.warning("[music_audit_audio] 开始 model=%s audio=%s", model, url[:120])
        raw, usage = dashscope_audio_completion(
            url,
            _AUDIO_PROMPT,
            temperature=0.0,
            timeout=120,
            response_format={"type": "json_object"},
        )
        parsed = parse_audit_model(raw, MusicAudioAuditResult)
        result = {
            "moodTags": parsed.moodTags,
            "genre": parsed.genre.strip()[:40],
            "energy": parsed.energy,
            "vocal": bool(parsed.vocal),
            "safe": parsed.safe,
            "risk": parsed.risk,
            "reasons": [str(item).strip()[:120] for item in parsed.reasons if str(item).strip()][:8],
        }
        logger.warning(
            "[music_audit_audio] 完成 safe=%s risk=%s genre=%s",
            parsed.safe,
            parsed.risk,
            result["genre"],
        )
        return result, usage_item(usage, "music_audit_audio")
    except Exception as exc:
        logger.exception("歌曲音频审核失败，标记 serviceError audio_url=%s err=%s", url[:120], exc)
        fallback = {
            "moodTags": [],
            "genre": "",
            "energy": "",
            "vocal": False,
            "safe": True,
            "risk": "medium",
            "serviceError": True,
            "reasons": [],
        }
        return fallback, usage_item({}, "music_audit_audio")
