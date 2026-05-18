"""
同步审核接口(短文本/单图):
  POST /api/v1/validate-text        json    {"content": "..."}  -> {"allow": bool, "msg": str, "cached": bool}
  POST /api/v1/validate-image       multipart, field=file       -> {"allow": bool}

帖子主体审核已改为异步走 LangGraph; 这两个接口给短内容场景保留:
  - 用户头像审核
  - 帖子回复 / 楼中楼审核
  - 私信内容审核
  - 表情商城 / Emoji 元数据审核
"""
from __future__ import annotations

import logging

from flask import jsonify, request
from langchain_core.messages import HumanMessage

from api import api
from clients.llm import text_llm, vision_llm, vision_llm_fallback
from config import settings
from graphs.prompts import IMAGE_AUDIT_TEMPLATE, IMAGE_DESC_PROMPT, TEXT_AUDIT_TEMPLATE
from utils import cache as semantic_cache
from utils.html import clean_html
from utils.image import to_data_url, validate_image_bytes

logger = logging.getLogger(__name__)

_IMG_MAX = int(settings.image.get("max_bytes", 10 * 1024 * 1024))


def _ok(**fields):
    fields.setdefault("code", 200)
    return jsonify(fields), 200


def _extract_text(resp) -> str:
    content = getattr(resp, "content", resp)
    if isinstance(content, list) and content:
        first = content[0]
        text = first.get("text", "") if isinstance(first, dict) else str(first)
    elif isinstance(content, str):
        text = content
    else:
        text = str(content)
    return text.strip()


@api.route("/validate-text", methods=["POST"])
def validate_text():
    data = request.get_json(silent=True) or {}
    if "content" not in data:
        return jsonify({"code": 400, "allow": False, "msg": "Missing content field"}), 400

    plain = clean_html(data["content"])
    if not plain:
        return _ok(allow=True, msg="Empty content", cached=False)

    cached = semantic_cache.find_match(plain)
    if cached:
        return _ok(allow=cached["allow"], msg=cached.get("msg", "OK"), cached=True)

    try:
        chain = TEXT_AUDIT_TEMPLATE | text_llm()
        resp = chain.invoke({"title": data.get("title", "") or "", "text": plain})
    except Exception:
        logger.exception("文本审核 LLM 调用失败")
        return jsonify({"code": 500, "allow": False, "msg": "审核服务暂时不可用"}), 500

    raw = _extract_text(resp)
    is_allowed = raw.upper() == "YES"
    final_msg = "OK" if is_allowed else raw
    semantic_cache.save(plain, {"allow": is_allowed, "msg": final_msg})
    return _ok(allow=is_allowed, msg=final_msg, cached=False)


@api.route("/validate-image", methods=["POST"])
def validate_image():
    if "file" not in request.files:
        return jsonify({"code": 400, "allow": False, "msg": "Missing file field"}), 400

    file = request.files["file"]
    if not file or not file.filename:
        return jsonify({"code": 400, "allow": False, "msg": "Empty file"}), 400

    image_data = file.read()
    if not image_data:
        return jsonify({"code": 400, "allow": False, "msg": "File content is empty"}), 400
    if len(image_data) > _IMG_MAX:
        return jsonify({"code": 413, "allow": False, "msg": "File too large"}), 413

    fmt = validate_image_bytes(image_data)
    if not fmt:
        return jsonify({"code": 415, "allow": False, "msg": "Unsupported format"}), 415

    try:
        data_url = to_data_url(image_data, fmt)
        try:
            resp = vision_llm().invoke([HumanMessage(content=[
                {"image": data_url},
                {"text": IMAGE_DESC_PROMPT},
            ])])
        except Exception:
            logger.warning("validate-image: vl-flash 失败，尝试 vl-plus")
            resp = vision_llm_fallback().invoke([HumanMessage(content=[
                {"image": data_url},
                {"text": IMAGE_DESC_PROMPT},
            ])])
        desc = _extract_text(resp)
        if not desc:
            return _ok(allow=False, msg="图片描述为空")
        chain = IMAGE_AUDIT_TEMPLATE | text_llm()
        verdict = _extract_text(chain.invoke({"desc": desc}))
    except Exception:
        logger.exception("图片审核异常")
        return jsonify({"code": 500, "allow": False, "msg": "图片审核服务异常"}), 500

    return _ok(allow=verdict.startswith("是"))
