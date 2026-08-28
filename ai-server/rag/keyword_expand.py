"""标题检索词扩展：向量库只存标题与短检索词，不存正文."""

from __future__ import annotations

import logging
import re

from config import settings

logger = logging.getLogger(__name__)

# 词表真源在 config.yaml 的 rag.topic_expansions（运营数据，可不改代码调整）。
# 这里只保留配置缺失时的兜底，避免检索扩展整体失效。
_FALLBACK_TOPIC_EXPANSIONS: dict[str, list[str]] = {
    "四川": ["四川", "川西", "四川西部", "川西高原", "甘孜", "阿坝"],
    "川西": ["四川", "四川西部", "川西高原", "甘孜", "阿坝"],
    "雪山": ["雪山", "雪峰", "高原雪景", "冰川"],
    "西藏": ["西藏", "拉萨", "高原", "藏区"],
    "新疆": ["新疆", "天山", "喀纳斯", "伊犁"],
    "云南": ["云南", "大理", "丽江", "香格里拉"],
    "旅行": ["旅行", "旅游", "出游", "攻略"],
    "自驾": ["自驾", "自驾游", "公路旅行"],
    "美食": ["美食", "探店", "好吃", "餐厅"],
    "咖啡": ["咖啡", "咖啡馆", "拿铁"],
    "猫": ["猫咪", "萌宠", "铲屎官"],
    "狗": ["狗狗", "萌宠", "遛狗"],
    "摄影": ["摄影", "旅拍", "扫街", "出片"],
    "java": ["Java", "后端", "Spring"],
    "python": ["Python", "爬虫", "数据分析"],
    "前端": ["前端", "Vue", "React", "页面"],
}


def _load_topic_expansions() -> dict[str, list[str]]:
    """从配置读取主题扩展词表；结构非法或为空时回退内置词表."""
    raw = settings.rag.get("topic_expansions")
    if not isinstance(raw, dict) or not raw:
        return dict(_FALLBACK_TOPIC_EXPANSIONS)
    parsed: dict[str, list[str]] = {}
    for key, value in raw.items():
        topic = str(key or "").strip()
        if not topic:
            continue
        if isinstance(value, str):
            phrases = [value.strip()] if value.strip() else []
        elif isinstance(value, list):
            phrases = [str(item).strip() for item in value if str(item or "").strip()]
        else:
            continue
        if phrases:
            parsed[topic] = phrases
    if not parsed:
        logger.warning("[keyword_expand] rag.topic_expansions 结构非法，回退内置词表")
        return dict(_FALLBACK_TOPIC_EXPANSIONS)
    return parsed


# 与 utils/rag_enhance.py 一致，配置在模块加载时取快照，改 yaml 需重启生效
_TOPIC_EXPANSIONS: dict[str, list[str]] = _load_topic_expansions()

# 词表改动会影响入库 embedding 文本，历史向量与新查询扩展口径将不一致；
# 打印版本号便于判断线上索引是否需要重建。
TOPIC_EXPANSIONS_VERSION = str(settings.rag.get("topic_expansions_version", "unset"))
logger.info(
    "[keyword_expand] 主题扩展词表已加载 version=%s topics=%d",
    TOPIC_EXPANSIONS_VERSION,
    len(_TOPIC_EXPANSIONS),
)


def _split_title_chunks(title: str) -> list[str]:
    t = (title or "").strip()
    if not t:
        return []
    parts: list[str] = []
    for seg in re.split(r"[,，、\s|/]+", t):
        s = seg.strip()
        if 2 <= len(s) <= 20 and s not in parts:
            parts.append(s)
    if len(t) <= 24 and t not in parts:
        parts.insert(0, t)
    return parts[:6]


def expand_title_keywords(title: str, extra: list[str] | None = None) -> list[str]:
    """
    从标题生成检索用词列表（去重），用于向量 embedding，不包含正文。
    """
    title = (title or "").strip()
    out: list[str] = []
    for chunk in _split_title_chunks(title):
        if chunk not in out:
            out.append(chunk)
    lower = title.lower()
    for key, phrases in _TOPIC_EXPANSIONS.items():
        if key in title or key in lower:
            for p in phrases:
                if p not in out:
                    out.append(p)
    if title and title not in out:
        out.insert(0, title[:40])
    for item in extra or []:
        s = (item or "").strip()
        if 2 <= len(s) <= 16 and s not in out:
            out.append(s)
    return out[:14]


def expand_search_query(query: str) -> str:
    """搜索框查询扩展：与入库检索词同义联动（如 四川 ↔ 川西）."""
    terms = expand_search_term_list(query)
    return "\n".join(terms)[:512]


def expand_search_term_list(query: str) -> list[str]:
    """扁平同义/分词列表，供 Java 侧与 hybrid_rank 共用同一扩展逻辑."""
    q = (query or "").strip()
    if not q:
        return []
    parts: list[str] = [q]
    lower = q.lower()
    for key, phrases in _TOPIC_EXPANSIONS.items():
        if key in q or key in lower:
            parts.extend(phrases)
        elif any(p in q or p in lower for p in phrases):
            if key not in parts:
                parts.append(key)
            parts.extend(phrases)
    for seg in re.split(r"[,，、\s|/]+", q):
        s = seg.strip()
        if 2 <= len(s) <= 24 and s not in parts:
            parts.append(s)
    return list(dict.fromkeys(x for x in parts if x))[:12]


def build_rag_embed_text(
    title: str, *, user_tags: list[str] | None = None
) -> tuple[str, list[str], str]:
    """
    返回 (redis 存储 doc 摘要, 检索词列表, embedding 输入文本).
    embedding 输入为若干短句拼接，不含正文。
    """
    keywords = expand_title_keywords(title, user_tags)
    lines = [f"标题：{title[:80]}"]
    if keywords:
        lines.append("检索词：" + "；".join(keywords))
    embed_lines = [title] + keywords
    embed_blob = "\n".join(dict.fromkeys(x for x in embed_lines if x))
    doc = "\n".join(lines)
    return doc, keywords, embed_blob
