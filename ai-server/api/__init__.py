"""Flask 蓝图集合"""
from flask import Blueprint

api = Blueprint("api", __name__, url_prefix="/api/v1")

from api import health  # noqa: F401, E402
from api import search  # noqa: F401, E402
from api import summary  # noqa: F401, E402
from api import validate  # noqa: F401, E402
from api import mascot  # noqa: F401, E402
from api import ai_hub  # noqa: F401, E402

__all__ = ["api"]
