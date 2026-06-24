"""Tavily Search API 客户端（供 MCP tavily_search 工具调用）."""

from __future__ import annotations

import logging
from typing import Any

import requests

from config import settings

logger = logging.getLogger(__name__)

_SEARCH_URL = "https://api.tavily.com/search"


class TavilySearchClient:
    def __init__(self) -> None:
        cfg = settings.tavily
        self._api_key = (cfg.get("api_key") or "").strip()
        self._timeout = int(cfg.get("timeout", 25))

    def is_configured(self) -> bool:
        return bool(self._api_key)

    def search(
        self,
        query: str,
        *,
        max_results: int = 5,
        search_depth: str = "basic",
        include_answer: bool = True,
        include_images: bool = False,
    ) -> dict[str, Any]:
        if not self.is_configured():
            raise ValueError("TAVILY_API_KEY 未配置")
        payload = {
            "query": query.strip(),
            "max_results": max(1, min(20, max_results)),
            "search_depth": search_depth or "basic",
            "include_answer": include_answer,
            "include_images": include_images,
        }
        r = requests.post(
            _SEARCH_URL,
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=self._timeout,
        )
        if not r.ok:
            logger.warning("Tavily HTTP %s: %s", r.status_code, r.text[:400])
            r.raise_for_status()
        data = r.json()
        if not isinstance(data, dict):
            raise ValueError("Tavily 响应格式异常")
        return data

    @staticmethod
    def _normalize_image_url(raw: Any) -> str:
        url = str(raw or "").strip()
        if not url.startswith(("https://", "http://")):
            return ""
        if len(url) > 2048:
            return ""
        return url

    def pick_illustration_image(self, data: dict[str, Any]) -> str:
        """从 Tavily 响应中选取一张可用于说明的配图（仅一张）."""
        images = data.get("images")
        if isinstance(images, list):
            for item in images:
                url = self._normalize_image_url(item)
                if url:
                    return url
        results = data.get("results") or []
        if isinstance(results, list):
            for item in results:
                if not isinstance(item, dict):
                    continue
                url = self._normalize_image_url(item.get("image"))
                if url:
                    return url
        return ""

    def format_search_data(
        self,
        data: dict[str, Any],
        *,
        max_results: int = 5,
    ) -> str:
        lines: list[str] = []
        answer = data.get("answer")
        if isinstance(answer, str) and answer.strip():
            lines.append(f"【搜索摘要】{answer.strip()}")
        results = data.get("results") or []
        if isinstance(results, list):
            for i, item in enumerate(results[:max_results], 1):
                if not isinstance(item, dict):
                    continue
                title = str(item.get("title") or "").strip()
                url = str(item.get("url") or "").strip()
                content = str(item.get("content") or "").strip()
                if not title and not content:
                    continue
                block = f"{i}. {title}"
                if url:
                    block += f" ({url})"
                if content:
                    block += f"\n   {content[:500]}"
                lines.append(block)
        if not lines:
            return "未找到相关网页结果。"
        return "\n".join(lines)

    def search_for_context(
        self,
        query: str,
        *,
        max_results: int = 5,
        search_depth: str = "basic",
        include_images: bool = False,
    ) -> str:
        """格式化为可注入 LLM / 生图 prompt 的文本."""
        data = self.search(
            query,
            max_results=max_results,
            search_depth=search_depth,
            include_answer=True,
            include_images=include_images,
        )
        return self.format_search_data(data, max_results=max_results)

    def search_for_chat(
        self,
        query: str,
        *,
        max_results: int = 5,
        search_depth: str = "basic",
    ) -> tuple[str, str]:
        """看板娘联网：返回 (文本上下文, 配图 URL 或空)."""
        data = self.search(
            query,
            max_results=max_results,
            search_depth=search_depth,
            include_answer=True,
            include_images=True,
        )
        text = self.format_search_data(data, max_results=max_results)
        image_url = self.pick_illustration_image(data)
        return text, image_url
