"""内网 API 密钥校验（Java BFF / 后台任务调用）."""

from __future__ import annotations

from flask import request

from config import env_truthy, settings


def require_internal_key() -> bool:
    if env_truthy("AI_REQUIRE_INTERNAL_KEY"):
        return True
    if env_truthy("AI_ALLOW_EMPTY_INTERNAL_KEY"):
        return False
    sec = settings.raw.get("security", {}) or {}
    return bool(sec.get("require_internal_key", False))


def internal_key_configured() -> str:
    mascot = (settings.mascot.get("internal_key") or "").strip()
    ai_hub = (settings.ai_hub.get("internal_key") or "").strip()
    return mascot or ai_hub


def assert_startup_internal_keys() -> None:
    if not require_internal_key():
        return
    if not internal_key_configured():
        raise SystemExit(
            "生产环境须配置 MASCOT_INTERNAL_KEY 或 AI_HUB_INTERNAL_KEY "
            "（security.require_internal_key=true）"
        )


def internal_auth_ok(expected_key: str) -> bool:
    expected = (expected_key or "").strip()
    if require_internal_key() and not expected:
        return False
    if not expected:
        return True
    got = (request.headers.get("X-Internal-Key") or "").strip()
    return got == expected
