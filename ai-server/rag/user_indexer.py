"""用户昵称向量索引（注册、改昵称时写入）."""

from __future__ import annotations

import logging

from clients.dashscope_embedding import embed_rag_document_meta
from rag.store import save_user_index

logger = logging.getLogger(__name__)


def build_user_embed_text(*, nickname: str) -> tuple[str, str]:
    nick = (nickname or "").strip()
    return (f"昵称：{nick[:40]}", nick) if nick else ("", "")


def index_user_profile(payload: dict) -> dict:
    user_id = int(payload.get("userId") or 0)
    if user_id <= 0:
        raise ValueError("userId required")
    doc, embed_text = build_user_embed_text(
        nickname=str(payload.get("nickname") or ""),
    )
    if not embed_text.strip():
        raise ValueError("nickname empty")
    meta = embed_rag_document_meta(doc_text=embed_text, video_or_image_url="", media_type=0)
    if not meta:
        raise RuntimeError("user embedding failed")
    vec, model_used = meta
    save_user_index(user_id, doc=doc, embedding=vec, embedding_model=model_used)
    logger.info("[rag] indexed userId=%s model=%s", user_id, model_used)
    return {"userId": user_id}
