"""审核通过后的帖子文档加载：仅标题+检索词向量，不写入正文."""

from __future__ import annotations

from rag.keyword_expand import build_rag_embed_text


def load_text_article_doc(
    *,
    title: str,
    content: str = "",
    summary: str = "",
    author_nickname: str = "",
    user_tags: list[str] | None = None,
) -> tuple[str, list[str], str]:
    """文本帖：doc 为展示用摘要；embed_text 为向量输入（无正文）."""
    _ = content, summary, author_nickname
    doc, keywords, embed_text = build_rag_embed_text(title, user_tags=user_tags)
    return doc, keywords, embed_text


def load_video_article_doc(
    *,
    title: str,
    content: str = "",
    summary: str = "",
    video_url: str = "",
    cover_url: str = "",
    author_nickname: str = "",
    user_tags: list[str] | None = None,
) -> tuple[str, list[str], str, str]:
    """视频帖：向量仍以标题+检索词为主；多模态可选用封面/视频."""
    _ = content, summary, author_nickname
    doc, keywords, embed_text = build_rag_embed_text(title, user_tags=user_tags)
    tags = (keywords + ["视频"])[:12]
    embed_input = (video_url or "").strip() or (cover_url or "").strip() or embed_text
    return doc, tags, embed_text, embed_input
