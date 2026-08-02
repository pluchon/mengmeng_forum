"""表情商城发布后，将标题与说明写入独立 RAG 索引。"""

from __future__ import annotations

import logging
from typing import Any

from clients.dashscope_embedding import embed_rag_document
from rag.store import save_emoji_index

logger = logging.getLogger(__name__)


def index_emoji_shop(payload: dict[str, Any]) -> dict[str, Any]:
    shop_id = int(payload.get("shopId") or 0)
    if shop_id <= 0:
        raise ValueError("shopId required")
    name = str(payload.get("name") or "").strip()
    description = str(payload.get("description") or "").strip()
    if not name:
        raise ValueError("name required")
    doc = f"表情包标题：{name}\n表情包说明：{description}".strip()
    embedding = embed_rag_document(doc_text=doc, media_type=0)
    if not embedding:
        raise RuntimeError("emoji embedding failed")
    save_emoji_index(shop_id, doc=doc, embedding=embedding)
    logger.info("[rag] indexed emoji shopId=%s", shop_id)
    return {"shopId": shop_id}
