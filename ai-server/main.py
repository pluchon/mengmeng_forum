"""
ai-server 启动入口.

一个进程承担两件事:
  1) Flask 只暴露健康检查与统一 AI Gateway
  2) 后台 daemon 线程消费 q-audit-article 跑 LangGraph, 把结果回投 forum.audit.result

小项目 / 低并发, 这种"单进程多职责"足够简单透明.
后续要扩多 worker, 把 audit_worker.start_in_background() 改成多线程或拆出独立进程即可.
"""
from __future__ import annotations

import logging
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path

_SCRIPT_DIR = Path(__file__).resolve().parent
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from flask import Flask

from api import api as api_blueprint
from config import settings
from workers import audit_worker


def _setup_logging() -> None:
    logging_cfg = settings.logging_cfg
    level_name = (logging_cfg.get("level") or "WARNING").upper()
    level = getattr(logging, level_name, logging.WARNING)
    log_file = Path(
        logging_cfg.get("file")
        or "../logs/python-backend/ai-server/ai-server.log"
    )
    log_file.parent.mkdir(parents=True, exist_ok=True)
    formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=int(logging_cfg.get("max_bytes") or 20 * 1024 * 1024),
        backupCount=int(logging_cfg.get("backup_count") or 14),
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)
    logging.basicConfig(
        level=level,
        handlers=[stream_handler, file_handler],
        force=True,
    )
    logging.getLogger("werkzeug").setLevel(level)


def create_app() -> Flask:
    app = Flask(__name__)
    # 给上传留点 margin
    img_max = int(settings.image.get("max_bytes", 10 * 1024 * 1024))
    app.config["MAX_CONTENT_LENGTH"] = img_max + 2 * 1024 * 1024
    app.register_blueprint(api_blueprint)
    return app


def main() -> None:
    _setup_logging()
    logger = logging.getLogger("main")

    from security.internal_auth import assert_startup_internal_keys

    assert_startup_internal_keys()

    # 启动后台审核 worker (daemon=True, 主线程退出时自动结束)
    try:
        audit_worker.start_in_background()
    except Exception:
        logger.exception("启动 audit_worker 失败, 进程继续仅服务 REST API")

    try:
        from utils.site_help import refresh_site_help_cache
        refresh_site_help_cache()
    except Exception:
        logger.exception("站点帮助缓存预热失败")

    app = create_app()
    server = settings.server
    host = server.get("host", "0.0.0.0")
    port = int(server.get("port", 5000))
    app.run(host=host, port=port, debug=False, use_reloader=False)


if __name__ == "__main__":
    main()
