"""曲目发布后，将标题/艺人/画像写入独立 RAG 索引。"""

from __future__ import annotations

import json
import logging
from typing import Any

from clients.dashscope_embedding import embed_rag_document_meta
from rag.store import save_music_index

logger = logging.getLogger(__name__)


def index_music_track(payload: dict[str, Any]) -> dict[str, Any]:
    music_key = str(payload.get("musicKey") or "").strip()
    if not music_key:
        raise ValueError("musicKey required")
    title = str(payload.get("title") or "").strip()
    artist = str(payload.get("artist") or "").strip()
    genre = str(payload.get("genre") or "").strip()
    mood_tags = _mood_tags(payload.get("moodTags"))
    ai_profile = str(payload.get("aiProfile") or "").strip()
    if not title and not ai_profile:
        raise ValueError("title or aiProfile required")
    doc = "\n".join([
        f"曲目标题：{title}",
        f"艺人：{artist}",
        f"曲风：{genre}",
        f"情绪标签：{'、'.join(mood_tags)}",
        f"AI画像：{ai_profile[:600]}",
    ]).strip()
    meta = embed_rag_document_meta(doc_text=doc, media_type=0)
    if not meta:
        raise RuntimeError("music embedding failed")
    embedding, model_used = meta
    save_music_index(music_key, doc=doc, embedding=embedding, embedding_model=model_used)
    logger.info("[rag] indexed music musicKey=%s model=%s", music_key, model_used)
    return {"musicKey": music_key}


def _mood_tags(raw: Any) -> list[str]:
    if isinstance(raw, list):
        return [str(item).strip()[:40] for item in raw if str(item).strip()][:12]
    if isinstance(raw, str) and raw.strip():
        text = raw.strip()
        try:
            parsed = json.loads(text)
            if isinstance(parsed, list):
                return [str(item).strip()[:40] for item in parsed if str(item).strip()][:12]
        except json.JSONDecodeError:
            pass
        return [part.strip()[:40] for part in text.replace("，", ",").split(",") if part.strip()][:12]
    return []
