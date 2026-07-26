"""Flask 蓝图集合"""
from __future__ import annotations

from flask import Blueprint

api = Blueprint("api", __name__, url_prefix="/api/v1")

from api import health  # noqa: F401, E402
from api import gateway  # noqa: F401, E402

__all__ = ["api"]
