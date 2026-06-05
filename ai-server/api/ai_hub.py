"""
AI Hub: /api/v1/ai/write | cover-hints | image
"""

from __future__ import annotations

import logging
import time
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
    from clients.usage_util import attach_latency

    t0 = time.perf_counter()
    try:
        content, usage = run_ai_write(kind, messages)
    except Exception as ex:
        logger.exception("ai_write 失败 kind=%s", kind)
        msg = str(ex).strip()[:400] or "ai write failed"
        return jsonify({"code": 500, "msg": msg}), 500
    return jsonify({"code": 200, "msg": "ok", "data": {"content": content, "usage": attach_latency(usage, t0)}})


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
        "你是论坛封面配图助手。根据用户正文提炼一个且仅一个「AI 绘图提示词」，"
        "必须严格使用以下单行模板（不要换行、不要列表、不要编号、不要引号包裹整句）：\n"
        "帮我画一张论坛帖子封面图，主题是【用不超过12字概括核心主题】，"
        "画面元素【用不超过20字描述1个主视觉，禁止并列多个无关主题】，"
        "风格【写实/插画/二次元/水彩四选一】，氛围【温馨/热血/治愈/悬疑四选一】。\n"
        "禁止输出第二套方案、禁止 markdown、禁止解释。"
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
    from clients.usage_util import attach_latency

    mcp_used = False
    t0 = time.perf_counter()
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
        "data": {"url": url, "usage": attach_latency(usage, t0), "mcp_used": mcp_used},
    })
