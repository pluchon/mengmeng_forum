"""用户昵称/用户名向量索引（注册、改昵称时写入）."""

from __future__ import annotations

import logging

from clients.dashscope_embedding import embed_rag_document
from rag.store import save_user_index

logger = logging.getLogger(__name__)


def build_user_embed_text(*, nickname: str, username: str, remark: str = "") -> str:
    nick = (nickname or "").strip()
    name = (username or "").strip()
    bio = (remark or "").strip()[:400]
    lines: list[str] = []
    if nick:
        lines.append(f"昵称：{nick[:40]}")
    if name:
        lines.append(f"用户名：{name[:40]}")
    if bio:
        lines.append(f"简介：{bio}")
    embed_blob = "\n".join(dict.fromkeys(x for x in [nick, name, bio] if x))
    doc = "\n".join(lines) if lines else embed_blob
    return doc, embed_blob or nick or name


def index_user_profile(payload: dict) -> dict:
    user_id = int(payload.get("userId") or 0)
    if user_id <= 0:
        raise ValueError("userId required")
    doc, embed_text = build_user_embed_text(
        nickname=str(payload.get("nickname") or ""),
        username=str(payload.get("username") or ""),
        remark=str(payload.get("remark") or ""),
    )
    if not embed_text.strip():
        raise ValueError("nickname/username empty")
    vec = embed_rag_document(doc_text=embed_text, video_or_image_url="", media_type=0)
    if not vec:
        raise RuntimeError("user embedding failed")
    save_user_index(user_id, doc=doc, embedding=vec)
    logger.info("[rag] indexed userId=%s", user_id)
    return {"userId": user_id}
