"""Tavily Search API 客户端（供 MCP tavily_search 工具调用）."""

from __future__ import annotations

import io
import json
import logging
import re
from typing import Any

import requests
from langchain_core.messages import HumanMessage
from PIL import Image, UnidentifiedImageError

from clients.llm import vision_llm
from config import settings
from utils.image import to_data_url

logger = logging.getLogger(__name__)

_SEARCH_URL = "https://api.tavily.com/search"
_VISION_IMAGE_MAX_SIDE = 768
_VISION_IMAGE_MAX_BYTES = 1_500_000
_VISION_CANDIDATE_LIMIT = 10

_IMAGE_REVIEW_PROMPT = """你是联网图片审核器。请仅依据图片内容，筛选真正适合作为用户查询图集的候选。
用户查询：{query}

通过条件：图片的主要主体与查询的实体或主题明确一致。
拒绝条件：仅有数字、Logo、网站图标、无关人物或角色、泛用插画、拼贴封面、无法确认主体的图片。
不要因为图片来源网页标题相关就放行，必须看图片本身。

只返回 JSON：{{"accepted":[{{"index":候选编号,"score":0到100的整数,"title":"不超过20字的图片说明"}}]}}。
按 score 降序，最多 5 张；不确定时不要加入 accepted。"""


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
        if not url.startswith("https://"):
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
        """收集 Tavily 返回的图片候选，交由视觉模型决定最终展示结果。"""
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

    def review_image_gallery(
        self,
        query: str,
        candidates: list[dict[str, str]],
    ) -> list[dict[str, str]]:
        """用视觉模型审核候选图，宁缺毋滥地保留最多五张。"""
        prepared: list[tuple[int, dict[str, str], str]] = []
        for candidate_index, item in enumerate(candidates[:_VISION_CANDIDATE_LIMIT], 1):
            data_url = self._candidate_to_vision_data_url(item.get("url", ""))
            if data_url:
                prepared.append((candidate_index, item, data_url))
        if not prepared:
            return []

        content: list[dict[str, str]] = []
        for candidate_index, _, data_url in prepared:
            content.append({"text": f"候选 {candidate_index}"})
            content.append({"image": data_url})
        content.append({"text": _IMAGE_REVIEW_PROMPT.format(query=query[:200])})

        try:
            response = vision_llm().invoke([HumanMessage(content=content)])
            accepted = self._parse_review_result(str(response.content or ""))
        except Exception:
            logger.exception("联网图片视觉审核失败")
            return []

        candidate_by_index = {index: item for index, item, _ in prepared}
        reviewed: list[dict[str, str]] = []
        seen: set[str] = set()
        for item in accepted:
            candidate_index = item.get("index")
            if not isinstance(candidate_index, int):
                continue
            candidate = candidate_by_index.get(candidate_index)
            if not candidate:
                continue
            url = candidate.get("url", "")
            if not url or url in seen:
                continue
            seen.add(url)
            title = str(item.get("title") or candidate.get("title") or "").strip()
            reviewed.append({
                "url": url,
                "title": title[:120],
                "source": candidate.get("source", "")[:160],
            })
            if len(reviewed) >= 5:
                break
        return reviewed

    def _candidate_to_vision_data_url(self, url: str) -> str:
        if not self._normalize_image_url(url):
            return ""
        try:
            response = requests.get(
                url,
                timeout=min(self._timeout, 12),
                stream=True,
                allow_redirects=False,
                headers={"Accept": "image/*"},
            )
            response.raise_for_status()
            data = bytearray()
            for chunk in response.iter_content(chunk_size=65_536):
                data.extend(chunk)
                if len(data) > _VISION_IMAGE_MAX_BYTES:
                    return ""
            with Image.open(io.BytesIO(data)) as image:
                image.thumbnail((_VISION_IMAGE_MAX_SIDE, _VISION_IMAGE_MAX_SIDE))
                normalized = image.convert("RGB")
                output = io.BytesIO()
                normalized.save(output, format="JPEG", quality=82, optimize=True)
            return to_data_url(output.getvalue(), "jpeg")
        except (OSError, requests.RequestException, UnidentifiedImageError):
            logger.info("联网图片候选无法用于视觉审核 host=%s", url.split("/")[2])
            return ""

    @staticmethod
    def _parse_review_result(raw: str) -> list[dict[str, Any]]:
        match = re.search(r"\{[\s\S]*\}", raw or "")
        if not match:
            return []
        try:
            payload = json.loads(match.group(0))
        except json.JSONDecodeError:
            return []
        accepted = payload.get("accepted") if isinstance(payload, dict) else None
        if not isinstance(accepted, list):
            return []
        valid = [item for item in accepted if isinstance(item, dict)]
        return sorted(valid, key=TavilySearchClient._review_score, reverse=True)

    @staticmethod
    def _review_score(item: dict[str, Any]) -> int:
        try:
            return int(item.get("score") or 0)
        except (TypeError, ValueError):
            return 0

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
        candidates = self.pick_image_gallery(data, max_images=_VISION_CANDIDATE_LIMIT) if include_images else []
        return text, self.review_image_gallery(query, candidates) if candidates else []
