"""
集中读取 config.yaml; 通过 settings 单例供其他模块按属性访问.
也允许用环境变量覆盖少数关键密钥(后续逐步迁移到 vault).
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any

import yaml

logger = logging.getLogger(__name__)

_CONFIG_PATH = Path(os.environ.get("AI_SERVER_CONFIG", "config.yaml")).resolve()


def _load() -> dict[str, Any]:
    if not _CONFIG_PATH.exists():
        raise FileNotFoundError(
            f"config.yaml 不存在: {_CONFIG_PATH}; 请确认工作目录或设置 AI_SERVER_CONFIG"
        )
    with _CONFIG_PATH.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


class Settings:
    def __init__(self, raw: dict[str, Any]):
        self.raw = raw
        # 环境变量覆盖关键密钥
        ds = raw.get("dashscope", {})
        ds["api_key"] = os.environ.get("DASHSCOPE_API_KEY", ds.get("api_key"))
        rd = raw.get("redis", {})
        rd["password"] = os.environ.get("REDIS_PASSWORD", rd.get("password"))
        rb = raw.get("rabbitmq", {})
        rb["password"] = os.environ.get("RABBITMQ_PASSWORD", rb.get("password"))
        pg = raw.get("postgres", {})
        pg["password"] = os.environ.get("POSTGRES_PASSWORD", pg.get("password"))
        mc = raw.get("mascot", {}) or {}
        mc["internal_key"] = os.environ.get("MASCOT_INTERNAL_KEY", mc.get("internal_key", ""))
        raw["mascot"] = mc

        ds = raw.get("deepseek", {}) or {}
        ds["api_key"] = os.environ.get("DEEPSEEK_API_KEY", ds.get("api_key"))
        raw["deepseek"] = ds

        hu = raw.get("huanapi", {}) or {}
        hu["base_url"] = os.environ.get("HUANAPI_BASE_URL", hu.get("base_url"))
        hu["image_key"] = os.environ.get("HUANAPI_IMAGE_KEY", hu.get("image_key"))
        hu["gemini_key"] = os.environ.get("HUANAPI_GEMINI_KEY", hu.get("gemini_key"))
        hu["claude_key"] = os.environ.get(
            "HUANAPI_CLAUDE_KEY",
            hu.get("claude_key") or hu.get("gemini_key"),
        )
        raw["huanapi"] = hu

        ah = raw.get("ai_hub", {}) or {}
        ah["internal_key"] = os.environ.get(
            "AI_HUB_INTERNAL_KEY",
            os.environ.get("MASCOT_INTERNAL_KEY", ah.get("internal_key", mc.get("internal_key", ""))),
        )
        raw["ai_hub"] = ah

        tv = raw.get("tavily", {}) or {}
        tv["api_key"] = os.environ.get("TAVILY_API_KEY", tv.get("api_key"))
        raw["tavily"] = tv

    @property
    def server(self) -> dict[str, Any]: return self.raw.get("server", {})
    @property
    def logging_cfg(self) -> dict[str, Any]: return self.raw.get("logging", {})
    @property
    def dashscope(self) -> dict[str, Any]: return self.raw.get("dashscope", {})
    @property
    def redis(self) -> dict[str, Any]: return self.raw.get("redis", {})
    @property
    def rabbitmq(self) -> dict[str, Any]: return self.raw.get("rabbitmq", {})
    @property
    def postgres(self) -> dict[str, Any]: return self.raw.get("postgres", {})
    @property
    def audit(self) -> dict[str, Any]: return self.raw.get("audit", {})
    @property
    def cache(self) -> dict[str, Any]: return self.raw.get("cache", {})
    @property
    def rag(self) -> dict[str, Any]: return self.raw.get("rag", {})
    @property
    def image(self) -> dict[str, Any]: return self.raw.get("image", {})

    @property
    def mascot(self) -> dict[str, Any]:
        return self.raw.get("mascot", {})

    @property
    def deepseek(self) -> dict[str, Any]:
        return self.raw.get("deepseek", {})

    @property
    def huanapi(self) -> dict[str, Any]:
        return self.raw.get("huanapi", {})

    @property
    def ai_hub(self) -> dict[str, Any]:
        return self.raw.get("ai_hub", {})

    @property
    def tavily(self) -> dict[str, Any]:
        return self.raw.get("tavily", {})

    @property
    def mcp(self) -> dict[str, Any]:
        return self.raw.get("mcp", {})

    def pg_url(self) -> str:
        """LangGraph PostgresSaver 用的 conn string"""
        pg = self.postgres
        return (
            f"postgresql://{pg.get('user', 'postgres')}:{pg.get('password', '')}"
            f"@{pg.get('host', '127.0.0.1')}:{pg.get('port', 5432)}/{pg.get('db', 'forum_ai')}"
            "?sslmode=disable"
        )


settings = Settings(_load())
logger.info("加载配置完成: %s", _CONFIG_PATH)
