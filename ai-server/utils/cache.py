"""
文本审核语义近似缓存:
  1) 精确缓存(md5 key, 24h TTL)
  2) Rerank 语义相似度命中候选库(最近 100 条)
命中即跳过 LLM, 显著降低 AI 调用次数.
"""
from __future__ import annotations

import hashlib
import json
import logging

import redis
from dashscope import TextReRank

from clients.llm import dashscope_api_key, rerank_model_name
from clients.redis_client import redis_client
from config import settings

logger = logging.getLogger(__name__)

_C = settings.cache


def _exact_key(text: str) -> str:
    return f"{_C.get('exact_prefix', 'ai_audit:cache:exact:')}{hashlib.md5(text.encode('utf-8')).hexdigest()}"


def _loads_json(raw: object) -> dict | None:
    """解析 Redis 缓存 JSON；兼容历史脏数据，失败时返回 None 而非抛错。"""
    if raw is None:
        return None
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8", errors="replace")
    if not isinstance(raw, str):
        raw = str(raw)
    text = raw.strip()
    if not text:
        return None
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        logger.warning("跳过无效缓存 JSON: %s", text[:120])
        return None
    return data if isinstance(data, dict) else None


def _parse_candidates(raw_items: list) -> list[dict]:
    rows: list[dict] = []
    for raw in raw_items:
        entry = _loads_json(raw)
        if not entry:
            continue
        text = entry.get("text")
        result = entry.get("result")
        if not isinstance(text, str) or not isinstance(result, dict):
            logger.warning("跳过结构异常的缓存条目")
            continue
        rows.append(entry)
    return rows


def find_match(text: str) -> dict | None:
    """命中返回缓存的 result dict {allow, msg}; 未命中返回 None"""
    try:
        hit = redis_client.get(_exact_key(text))
    except redis.RedisError:
        logger.exception("Redis 精确缓存读取失败")
        return None
    if hit:
        cached = _loads_json(hit)
        if cached:
            logger.info("精确缓存命中")
            return cached
        logger.warning("精确缓存损坏，忽略后走审核")

    try:
        candidates_json = redis_client.lrange(_C.get("list_key", "ai_audit:text_list"),
                                              0, int(_C.get("candidate_topk", 50)) - 1)
    except redis.RedisError:
        logger.exception("Redis 候选列表读取失败")
        return None
    if not candidates_json:
        return None

    candidates = _parse_candidates(candidates_json)
    if not candidates:
        return None

    docs = [c["text"] for c in candidates]

    try:
        resp = TextReRank.call(
            model=rerank_model_name(),
            query=text,
            documents=docs,
            top_n=1,
            api_key=dashscope_api_key(),
        )
    except Exception:
        logger.exception("Rerank 调用异常")
        return None

    if resp.status_code != 200 or not resp.output.results:
        return None

    top = resp.output.results[0]
    threshold = float(_C.get("semantic_threshold", 0.7))
    if top.relevance_score < threshold:
        return None
    match_text = docs[top.index]
    for c in candidates:
        if c["text"] == match_text:
            logger.info("语义命中 score=%.4f sample=%s...", top.relevance_score, match_text[:20])
            return c["result"]
    return None


def save(text: str, result: dict) -> None:
    try:
        res_json = json.dumps(result, ensure_ascii=False)
        entry_json = json.dumps({"text": text, "result": result}, ensure_ascii=False)
        pipe = redis_client.pipeline()
        pipe.setex(_exact_key(text), int(_C.get("ttl", 86400)), res_json)
        pipe.lpush(_C.get("list_key", "ai_audit:text_list"), entry_json)
        pipe.ltrim(_C.get("list_key", "ai_audit:text_list"), 0, int(_C.get("list_max", 100)) - 1)
        pipe.execute()
    except redis.RedisError:
        logger.exception("写入语义缓存失败")
