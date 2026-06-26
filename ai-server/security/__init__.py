"""安全相关工具."""

from security.internal_auth import (
    assert_startup_internal_keys,
    internal_auth_ok,
    internal_key_configured,
    require_internal_key,
)

__all__ = [
    "assert_startup_internal_keys",
    "internal_auth_ok",
    "internal_key_configured",
    "require_internal_key",
]
