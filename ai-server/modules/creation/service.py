"""帖子创作模块的模型与生图能力。"""
from __future__ import annotations

import logging
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from clients.dashscope_image import dashscope_premium_text_to_image, dashscope_text_to_image
from clients.llm import flash_model_name
from graphs.text_generation import run_text_generation
from utils.image_mcp import enrich_image_prompt

logger = logging.getLogger(__name__)


class CreationConfigError(RuntimeError):
    """创作模块所需外部配置缺失。"""


def generate_polished_content(
    kind: str,
    title: str,
    content: str,
    editor_mode: str,
) -> tuple[str, dict[str, Any]]:
    title_part = f"帖子标题：{title}。" if title else ""
    natural_style = (
        "保持原意、事实、语气和段落层次，只优化表达的流畅度与可读性。"
        "使用自然、克制、像真人分享的中文；禁止机械套话、emoji、颜文字、图标列表、编号列表、"
        "Markdown 代码围栏、前言、解释或多套版本。"
    )
    if editor_mode == "markdown":
        output_rule = "只输出可直接替换的 Markdown 正文；除非原文已有必要结构，不新增标题或列表。"
    else:
        output_rule = "只输出可直接替换的富文本 HTML 片段，以 p 为主；不要 Markdown、完整 HTML 文档或代码围栏。"
    messages = [
        {"role": "system", "content": f"你是论坛正文润色助手。{title_part}{natural_style}{output_rule}"},
        {"role": "user", "content": content},
    ]
    return run_text_generation(kind, messages)


def generate_cover_hints(article: str) -> tuple[str, dict[str, Any]]:
    system = (
        "你是论坛封面策划助手。先理解正文的核心主题、情绪和唯一主视觉，再输出一个供生图模型直接理解的简短中文画面描述。"
        "只保留一个主体、一个场景和一种画风；不要复述正文、不要抽象口号、不要堆砌形容词、不要出现文字、水印、emoji、列表或多套方案。"
        "严格只输出一行，格式为：主题：…；画面：…；风格：…；氛围：…。每项简短明确，总长度不超过 90 字。"
    )
    messages: list[dict[str, str]] = [
        {"role": "system", "content": system},
        {"role": "user", "content": article[:12000]},
    ]
    model = flash_model_name()
    return dashscope_chat_completion(model, messages, temperature=0.3)


def generate_image(prompt: str, quality: str, *, enrich: bool = True) -> tuple[str, dict[str, Any], bool]:
    """生成一张图。quality: normal | premium。

    档位由看板娘的规划器按画面复杂度自主判定，Java 侧再按会员档位复核一次并
    据此扣额度（进阶档算两张），这里只负责按档位选模型。
    """
    enhanced_prompt, mcp_used = enrich_image_prompt(prompt) if enrich else (prompt, False)
    normalized = (quality or "normal").strip().lower()
    if normalized not in ("normal", "premium"):
        raise CreationConfigError("生图档位只能是 normal 或 premium")
    if normalized == "premium":
        url, usage = dashscope_premium_text_to_image(enhanced_prompt)
    else:
        url, usage = dashscope_text_to_image(enhanced_prompt)
    return url, usage, mcp_used
