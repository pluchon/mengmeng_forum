"""
ai-server 启动入口.

一个进程承担两件事:
  1) Flask 暴露同步 REST API (健康检查 / validate-text / validate-image / summarize / article-rag-search)
  2) 后台 daemon 线程消费 q-audit-article 跑 LangGraph, 把结果回投 forum.audit.result

小项目 / 低并发, 这种"单进程多职责"足够简单透明.
后续要扩多 worker, 把 audit_worker.start_in_background() 改成多线程或拆出独立进程即可.
"""
from __future__ import annotations

import logging

from flask import Flask

from api import api as api_blueprint
from config import settings
from workers import audit_worker


def _setup_logging():
    level_name = (settings.logging_cfg.get("level") or "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def create_app() -> Flask:
    app = Flask(__name__)
    # 给上传留点 margin
    img_max = int(settings.image.get("max_bytes", 10 * 1024 * 1024))
    app.config["MAX_CONTENT_LENGTH"] = img_max + 2 * 1024 * 1024
    app.register_blueprint(api_blueprint)
    return app


def main():
    _setup_logging()
    logger = logging.getLogger("main")

    # 启动后台审核 worker (daemon=True, 主线程退出时自动结束)
    try:
        audit_worker.start_in_background()
    except Exception:
        logger.exception("启动 audit_worker 失败, 进程继续仅服务 REST API")

    try:
        from utils.site_help import ensure_site_help_cached
        ensure_site_help_cached()
    except Exception:
        logger.exception("站点帮助缓存预热失败")

    app = create_app()
    server = settings.server
    host = server.get("host", "0.0.0.0")
    port = int(server.get("port", 5000))
    logger.info("ai-server 启动, 监听 %s:%d", host, port)
    app.run(host=host, port=port, debug=False, use_reloader=False)


if __name__ == "__main__":
    main()
