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

_AI_SERVER_ROOT = Path(__file__).resolve().parent
_CONFIG_PATH = Path(os.environ.get("AI_SERVER_CONFIG", str(_AI_SERVER_ROOT / "config.yaml"))).resolve()
_LOCAL_ENV_PATH = _AI_SERVER_ROOT.parent / "nginx" / ".env"


def _load_local_env() -> None:
    """本地直接启动 main.py 时读取 nginx/.env，显式环境变量始终优先。"""
    if not _LOCAL_ENV_PATH.exists():
        return
    try:
        for raw_line in _LOCAL_ENV_PATH.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if not key or key in os.environ:
                continue
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
                value = value[1:-1]
            os.environ[key] = value
    except OSError:
        logger.warning("读取本地 nginx/.env 失败，继续使用进程环境变量", exc_info=True)


_load_local_env()


def env_truthy(name: str) -> bool:
    """环境变量是否为真值（1 / true / yes / on，大小写不敏感）。"""
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes", "on")


def env_nonempty(name: str, fallback: Any) -> Any:
    """优先读取非空环境变量，空值继续使用配置文件默认值。"""
    value = os.environ.get(name)
    if value is None or not value.strip():
        return fallback
    return value.strip()


def rabbitmq_port(fallback: Any) -> int:
    """读取 Python 连接端口，兼容开发 Compose 的 RABBITMQ_AMQP_PORT。"""
    raw_port = env_nonempty(
        "RABBITMQ_PORT",
        env_nonempty("RABBITMQ_AMQP_PORT", fallback),
    )
    try:
        port = int(raw_port)
    except (TypeError, ValueError) as exc:
        raise ValueError("RABBITMQ_PORT 或 RABBITMQ_AMQP_PORT 必须是有效端口") from exc
    if not 1 <= port <= 65535:
        raise ValueError("RABBITMQ_PORT 或 RABBITMQ_AMQP_PORT 必须在 1-65535 范围内")
    return port


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
        rb["host"] = env_nonempty("RABBITMQ_HOST", rb.get("host"))
        rb["port"] = rabbitmq_port(rb.get("port", 5672))
        rb["virtual_host"] = env_nonempty(
            "RABBITMQ_VIRTUAL_HOST",
            env_nonempty("RABBITMQ_VHOST", rb.get("virtual_host")),
        )
        rb["username"] = env_nonempty(
            "RABBITMQ_USERNAME",
            env_nonempty("RABBITMQ_USER", rb.get("username")),
        )
        rb["password"] = env_nonempty("RABBITMQ_PASSWORD", rb.get("password"))
        raw["rabbitmq"] = rb
        pg = raw.get("postgres", {})
        pg["password"] = os.environ.get("POSTGRES_PASSWORD", pg.get("password"))
        mc = raw.get("mascot", {}) or {}
        mc["internal_key"] = env_nonempty(
            "MASCOT_INTERNAL_KEY",
            env_nonempty("FORUM_MASCOT_INTERNAL_KEY", mc.get("internal_key", "")),
        )
        raw["mascot"] = mc

        ds = raw.get("deepseek", {}) or {}
        ds["api_key"] = os.environ.get("DEEPSEEK_API_KEY", ds.get("api_key"))
        raw["deepseek"] = ds

        hu = raw.get("huanapi", {}) or {}
        hu["base_url"] = os.environ.get("HUANAPI_BASE_URL", hu.get("base_url"))
        hu["image_key"] = os.environ.get("HUANAPI_IMAGE_KEY", hu.get("image_key"))
        raw["huanapi"] = hu

        ah = raw.get("ai_hub", {}) or {}
        ah["internal_key"] = env_nonempty(
            "AI_HUB_INTERNAL_KEY",
            env_nonempty(
                "FORUM_AI_INTERNAL_KEY",
                env_nonempty("MASCOT_INTERNAL_KEY", mc.get("internal_key", "")),
            ),
        )
        raw["ai_hub"] = ah

        tv = raw.get("tavily", {}) or {}
        tv["api_key"] = os.environ.get("TAVILY_API_KEY", tv.get("api_key"))
        raw["tavily"] = tv

        bm = raw.get("baidu_map", {}) or {}
        bm["api_key"] = os.environ.get("BAIDU_MAP_API_KEY", bm.get("api_key"))
        raw["baidu_map"] = bm

        oss = raw.get("oss", {}) or {}
        oss["access_key_id"] = os.environ.get("ALIYUN_ACCESS_KEY_ID", oss.get("access_key_id", ""))
        oss["access_key_secret"] = os.environ.get(
            "ALIYUN_ACCESS_KEY_SECRET", oss.get("access_key_secret", "")
        )
        oss["bucket_name"] = os.environ.get("OSS_BUCKET_NAME", oss.get("bucket_name", ""))
        oss["url_prefix"] = os.environ.get("OSS_URL_PREFIX", oss.get("url_prefix", ""))
        oss["root_prefix"] = os.environ.get("OSS_ROOT_PREFIX", oss.get("root_prefix", ""))
        raw["oss"] = oss

        ff = raw.get("ffmpeg", {}) or {}
        ff["base_url"] = os.environ.get("FORUM_FFMPEG_URL", ff.get("base_url", "http://ffmpeg:8099"))
        ff["internal_key"] = os.environ.get(
            "FFMPEG_INTERNAL_KEY",
            os.environ.get("FORUM_FFMPEG_INTERNAL_KEY", ff.get("internal_key", "")),
        )
        raw["ffmpeg"] = ff

        fm = raw.get("forum", {}) or {}
        fm["base_url"] = os.environ.get("FORUM_BACKEND_BASE_URL", fm.get("base_url", "http://localhost:10086"))
        raw["forum"] = fm

        sec = raw.get("security", {}) or {}
        if env_truthy("AI_REQUIRE_INTERNAL_KEY"):
            sec["require_internal_key"] = True
        raw["security"] = sec

    @property
    def server(self) -> dict[str, Any]:
        return self.raw.get("server", {})

    @property
    def logging_cfg(self) -> dict[str, Any]:
        return self.raw.get("logging", {})

    @property
    def dashscope(self) -> dict[str, Any]:
        return self.raw.get("dashscope", {})

    @property
    def redis(self) -> dict[str, Any]:
        return self.raw.get("redis", {})

    @property
    def rabbitmq(self) -> dict[str, Any]:
        return self.raw.get("rabbitmq", {})

    @property
    def postgres(self) -> dict[str, Any]:
        return self.raw.get("postgres", {})

    @property
    def audit(self) -> dict[str, Any]:
        return self.raw.get("audit", {})

    @property
    def cache(self) -> dict[str, Any]:
        return self.raw.get("cache", {})

    @property
    def rag(self) -> dict[str, Any]:
        return self.raw.get("rag", {})

    @property
    def image(self) -> dict[str, Any]:
        return self.raw.get("image", {})

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

    @property
    def baidu_map(self) -> dict[str, Any]:
        return self.raw.get("baidu_map", {})

    @property
    def oss(self) -> dict[str, Any]:
        return self.raw.get("oss", {})

    @property
    def ffmpeg(self) -> dict[str, Any]:
        return self.raw.get("ffmpeg", {})

    @property
    def forum(self) -> dict[str, Any]:
        return self.raw.get("forum", {})

    @property
    def security(self) -> dict[str, Any]:
        return self.raw.get("security", {})

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
