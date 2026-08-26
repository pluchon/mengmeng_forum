"""审核通过后：标题检索词 -> qwen3-vl-embedding -> 写入 Redis（不含正文向量）."""

from __future__ import annotations

import logging

from clients.dashscope_embedding import embed_rag_document_meta
from rag.loaders import load_text_article_doc, load_video_article_doc
from rag.store import save_article_index

logger = logging.getLogger(__name__)


def index_published_article(payload: dict) -> dict:
    """
    payload: articleId, title, content(忽略向量), mediaType, videoUrl, coverUrl, summary, authorNickname, tagNames[]
    """
    article_id = int(payload.get("articleId") or 0)
    if article_id <= 0:
        raise ValueError("articleId required")
    title = str(payload.get("title") or "")
    media_type = int(payload.get("mediaType") or 0)
    video_url = str(payload.get("videoUrl") or "").strip()
    cover_url = str(payload.get("coverUrl") or "").strip()
    author = str(payload.get("authorNickname") or "")
    raw_tags = payload.get("tagNames") or payload.get("tags") or []
    user_tags: list[str] = []
    if isinstance(raw_tags, list):
        for t in raw_tags:
            s = str(t).strip()
            if s:
                user_tags.append(s[:16])

    if media_type == 1 and video_url:
        doc, tags, embed_text, embed_input = load_video_article_doc(
            title=title,
            video_url=video_url,
            cover_url=cover_url,
            author_nickname=author,
            user_tags=user_tags,
        )
        meta = embed_rag_document_meta(doc_text=embed_text, video_or_image_url=embed_input, media_type=1)
    else:
        doc, tags, embed_text = load_text_article_doc(
            title=title,
            author_nickname=author,
            user_tags=user_tags,
        )
        meta = embed_rag_document_meta(doc_text=embed_text, video_or_image_url=cover_url, media_type=0)

    if not meta:
        from clients.llm import embedding_model_name, embedding_text_fallback_model_name

        raise RuntimeError(
            "embedding failed: Dashscope 向量无结果（多模态="
            + embedding_model_name()
            + " 文本降级="
            + embedding_text_fallback_model_name()
            + "）。对话能用只说明 chat 密钥正常；向量走 MultiModalEmbedding/TextEmbedding，"
            "请查看 ai-server 控制台 MultiModalEmbedding/TextEmbedding 告警与 code/msg"
        )

    vec, model_used = meta
    save_article_index(
        article_id,
        doc=doc,
        tags=tags,
        media_type=media_type,
        embedding=vec,
        video_url=video_url,
        embedding_model=model_used,
    )
    logger.info("[rag] indexed articleId=%s mediaType=%s model=%s keywords=%s", article_id, media_type, model_used, tags)
    return {"articleId": article_id, "tags": tags, "mediaType": media_type}
