"""
摘要生成接口:
  POST /api/v1/summarize  json {"content": "..."} -> {"summary": "..."}
"""
from __future__ import annotations

import logging

from flask import jsonify, request

from api import api
from clients.llm import text_llm
from config import settings
from graphs.prompts import SUMMARY_TEMPLATE
from utils.html import clean_html

logger = logging.getLogger(__name__)

_MIN_LEN = int(settings.audit.get("summary_min_len", 50))


def _extract_text(resp) -> str:
    content = getattr(resp, "content", resp)
    if isinstance(content, list) and content:
        first = content[0]
        return first.get("text", "") if isinstance(first, dict) else str(first)
    return str(content).strip() if content else ""


@api.route("/summarize", methods=["POST"])
def summarize_text():
    data = request.get_json(silent=True) or {}
    if "content" not in data:
        return jsonify({"code": 400, "summary": "", "msg": "Missing content"}), 400

    plain = clean_html(data["content"])
    if len(plain) < _MIN_LEN:
        hint = f"当前帖子内容较少（共 {len(plain)} 字），建议包含更多内容后再尝试 AI 智能总结。"
        return jsonify({
            "code": 200,
            "summary": hint,
            "msg": "Content too short for summary",
        }), 200

    try:
        chain = SUMMARY_TEMPLATE | text_llm(temperature=0.3)
        summary = _extract_text(chain.invoke({"text": plain}))
        if not summary or summary.replace(" ", "") == plain.replace(" ", ""):
            hint = "AI 未能生成有效摘要，请充实正文后重试。"
            return jsonify({"code": 200, "summary": hint, "msg": "Summary too similar or empty"}), 200
    except Exception:
        logger.exception("摘要生成异常")
        return jsonify({"code": 500, "summary": "", "msg": "Summary generation failed"}), 500
    return jsonify({"code": 200, "summary": summary, "msg": "success"}), 200
