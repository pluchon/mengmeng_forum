"""
AI Hub: /api/v1/ai/write | cover-hints | image
"""

from __future__ import annotations

import logging
from typing import Any

from flask import jsonify, request

from api import api
from clients.dashscope_image import dashscope_text_to_image
from clients.deepseek_client import deepseek_chat_completion
from clients.huanapi_client import huanapi_images
from config import settings
from graphs.ai_write_graph import run_ai_write
from utils.image_mcp import enrich_image_prompt

logger = logging.getLogger(__name__)


def _internal_auth_ok() -> bool:
    expected = (settings.ai_hub.get("internal_key") or "").strip()
    if not expected:
        return True
    got = (request.headers.get("X-Internal-Key") or "").strip()
    return got == expected


def _clean_messages(raw: Any, max_turns: int = 24) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    if not isinstance(raw, list):
        return out
    for item in raw[-max_turns:]:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role", "")).strip().lower()
        content = str(item.get("content", "")).strip()
        if role not in ("system", "user", "assistant") or not content:
            continue
        out.append({"role": role, "content": content[:32000]})
    return out


@api.route("/ai/write", methods=["POST"])
def ai_write():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403
    data = request.get_json(silent=True) or {}
    kind = str(data.get("kind") or "").strip().lower()
    messages = _clean_messages(data.get("messages"))
    if not kind or not messages:
        return jsonify({"code": 400, "msg": "kind and messages required"}), 400
    try:
        content, usage = run_ai_write(kind, messages)
    except Exception:
        logger.exception("ai_write 失败 kind=%s", kind)
        return jsonify({"code": 500, "msg": "ai write failed"}), 500
    return jsonify({"code": 200, "msg": "ok", "data": {"content": content, "usage": usage}})


@api.route("/ai/cover-hints", methods=["POST"])
def ai_cover_hints():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403
    data = request.get_json(silent=True) or {}
    article = str(data.get("article_text") or "").strip()
    if not article:
        return jsonify({"code": 400, "msg": "article_text required"}), 400
    article = article[:12000]
    ds = settings.deepseek
    system = (
        "你是论坛编辑助手。根据正文列出 3～6 条「封面配图」要点（简短词组或短语），"
        "每条一行，不要编号以外的冗长解释。"
    )
    messages: list[dict[str, str]] = [
        {"role": "system", "content": system},
        {"role": "user", "content": article},
    ]
    try:
        base = ds.get("base_url") or "https://api.deepseek.com/v1"
        model = ds.get("model_flash") or "deepseek-v4-flash"
        key = ds.get("api_key") or ""
        content, usage = deepseek_chat_completion(base, key, model, messages)
    except Exception:
        logger.exception("cover-hints 失败")
        return jsonify({"code": 500, "msg": "cover hints failed"}), 500
    return jsonify({"code": 200, "msg": "ok", "data": {"content": content, "usage": usage}})


@api.route("/ai/image", methods=["POST"])
def ai_image():
    if not _internal_auth_ok():
        return jsonify({"code": 403, "msg": "invalid X-Internal-Key"}), 403
    data = request.get_json(silent=True) or {}
    prompt = str(data.get("prompt") or "").strip()
    quality = str(data.get("quality") or "normal").strip().lower()
    if not prompt:
        return jsonify({"code": 400, "msg": "prompt required"}), 400
    if quality not in ("normal", "premium"):
        return jsonify({"code": 400, "msg": "quality must be normal|premium"}), 400
    mcp_used = False
    try:
        prompt, mcp_used = enrich_image_prompt(prompt)
        if quality == "normal":
            url, usage = dashscope_text_to_image(prompt)
        else:
            hu = settings.huanapi
            base = str(hu.get("base_url") or "https://www.huanapi.com")
            img_key = str(hu.get("image_key") or "").strip()
            if not img_key:
                return jsonify({
                    "code": 503,
                    "msg": "GPT 生图未配置（HUANAPI_IMAGE_KEY）",
                }), 503
            premium_model = str(hu.get("model_image_premium") or "gpt-image-2").strip()
            if premium_model != "gpt-image-2":
                logger.warning(
                    "huanapi.model_image_premium=%r，将使用官方模型名 gpt-image-2",
                    premium_model,
                )
            url, usage = huanapi_images(base, img_key, "gpt-image-2", prompt)
    except Exception as exc:
        logger.exception("ai_image 失败 quality=%s", quality)
        msg = "image generation failed"
        if quality == "premium":
            detail = str(exc).strip()
            if "SSLError" in detail or "SSL" in detail:
                msg = (
                    "无法连接 HuanAPI（SSL/网络异常），请检查本机网络、代理或防火墙；"
                    "可在环境变量 HUANAPI_BASE_URL 指定网关地址"
                )
            elif detail:
                msg = detail[:500]
        return jsonify({"code": 500, "msg": msg}), 500
    return jsonify({
        "code": 200,
        "msg": "ok",
        "data": {"url": url, "usage": usage, "mcp_used": mcp_used},
    })
