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
        include_domains: list[str] | None = None,
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
        if include_domains:
            payload["include_domains"] = [str(domain).strip() for domain in include_domains if str(domain).strip()]
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

    def pick_image_gallery(self, data: dict[str, Any], *, max_images: int = 5) -> list[dict[str, str]]:
        """保留 Tavily 返回顺序，取最相关且可安全展示的图像。"""
        gallery: list[dict[str, str]] = []
        seen: set[str] = set()

        def append_item(raw: Any, *, title: str = "", source: str = "") -> None:
            if isinstance(raw, dict):
                url = self._normalize_image_url(raw.get("url") or raw.get("image"))
                item_title = str(raw.get("description") or raw.get("title") or title).strip()
                item_source = str(raw.get("source") or raw.get("url") or source).strip()
            else:
                url = self._normalize_image_url(raw)
                item_title = title
                item_source = source
            if not url or url in seen or len(gallery) >= max_images:
                return
            seen.add(url)
            gallery.append({"url": url, "title": item_title[:120], "source": item_source[:160]})

        images = data.get("images")
        if isinstance(images, list):
            for item in images:
                append_item(item)
        results = data.get("results")
        if isinstance(results, list):
            for result in results:
                if not isinstance(result, dict):
                    continue
                result_title = str(result.get("title") or "").strip()
                result_source = str(result.get("url") or "").strip()
                nested_images = result.get("images")
                if isinstance(nested_images, list):
                    for item in nested_images:
                        append_item(item, title=result_title, source=result_source)
                append_item(result.get("image"), title=result_title, source=result_source)
        return gallery

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
        include_images: bool = False,
        prefer_encyclopedia: bool = False,
    ) -> tuple[str, list[dict[str, str]]]:
        """看板娘联网：返回文字参考与可选图集，不为凑数补图。"""
        domains = None
        if include_images and prefer_encyclopedia:
            domains = ["baike.baidu.com", "zh.wikipedia.org", "en.wikipedia.org", "britannica.com"]
        data = self.search(
            query,
            max_results=max_results,
            search_depth=search_depth,
            include_answer=True,
            include_images=include_images,
            include_domains=domains,
        )
        text = self.format_search_data(data, max_results=max_results)
        return text, self.pick_image_gallery(data) if include_images else []
