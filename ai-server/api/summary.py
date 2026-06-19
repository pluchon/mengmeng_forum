"""
摘要生成接口:
  POST /api/v1/summarize  json {"content": "..."} -> {"summary": "..."}
  POST /api/v1/summarize/stream  SSE data: {"text": "..."}
"""
from __future__ import annotations

import json
import logging
from typing import Any

from flask import Response, jsonify, request, stream_with_context

from api import api
from clients.dashscope_chat_client import dashscope_stream_text_legacy, lc_messages_to_openai
from clients.llm import text_llm
from config import settings
from graphs.prompts import SUMMARY_TEMPLATE
from utils.html import clean_html

logger = logging.getLogger(__name__)

_MIN_LEN = int(settings.audit.get("summary_min_len", 50))


def _json_payload() -> dict[str, Any]:
    data = request.get_json(silent=True) or {}
    return data if isinstance(data, dict) else {}


def _extract_text(resp: object) -> str:
    content = getattr(resp, "content", resp)
    if isinstance(content, list) and content:
        first = content[0]
        return first.get("text", "") if isinstance(first, dict) else str(first)
    return str(content).strip() if content else ""


@api.route("/summarize", methods=["POST"])
def summarize_text():
    data = _json_payload()
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


@api.route("/summarize/stream", methods=["POST"])
def summarize_stream():
    data = _json_payload()
    if "content" not in data:
        return jsonify({"code": 400, "msg": "Missing content"}), 400

    plain = clean_html(data["content"])
    if len(plain) < _MIN_LEN:
        hint = f"当前帖子内容较少（共 {len(plain)} 字），建议包含更多内容后再尝试 AI 智能总结。"

        def short_hint():
            yield f"data: {json.dumps({'text': hint}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"

        return Response(
            stream_with_context(short_hint()),
            mimetype="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    model = text_llm(temperature=0.3)
    messages = lc_messages_to_openai(SUMMARY_TEMPLATE.format_messages(text=plain))

    def generate():
        try:
            for piece in dashscope_stream_text_legacy(model.model_name, messages, temperature=0.3):
                if piece:
                    yield f"data: {json.dumps({'text': piece}, ensure_ascii=False)}\n\n"
        except Exception:
            logger.exception("摘要流式生成异常")
            err = "AI 摘要生成暂时不可用，请稍后再试。"
            yield f"data: {json.dumps({'text': err}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
