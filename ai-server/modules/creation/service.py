"""帖子创作模块的模型与生图能力。"""
from __future__ import annotations

import logging
from typing import Any

from clients.dashscope_chat_client import dashscope_chat_completion
from clients.dashscope_image import dashscope_text_to_image
from clients.huanapi_client import huanapi_images
from config import settings
from graphs.ai_write_graph import run_ai_write
from utils.image_mcp import enrich_image_prompt

logger = logging.getLogger(__name__)


class CreationConfigError(RuntimeError):
    """创作模块所需外部配置缺失。"""


def generate_write_content(
    kind: str,
    messages: list[dict[str, str]],
) -> tuple[str, dict[str, Any]]:
    return run_ai_write(kind, messages)


def generate_cover_hints(article: str) -> tuple[str, dict[str, Any]]:
    system = (
        "你是论坛封面配图助手。根据用户正文提炼一个且仅一个「AI 绘图提示词」，"
        "必须严格使用以下单行模板（不要换行、不要列表、不要编号、不要引号包裹整句）：\n"
        "帮我画一张论坛帖子封面图，主题是【用不超过12字概括核心主题】，"
        "画面元素【用不超过20字描述1个主视觉，禁止并列多个无关主题】，"
        "风格【写实/插画/二次元/水彩四选一】，氛围【温馨/热血/治愈/悬疑四选一】。\n"
        "禁止输出第二套方案、禁止 markdown、禁止解释。"
    )
    messages: list[dict[str, str]] = [
        {"role": "system", "content": system},
        {"role": "user", "content": article[:12000]},
    ]
    model = settings.dashscope.get("model_text_flash") or settings.dashscope.get("model_text") or "qwen3.6-flash"
    return dashscope_chat_completion(model, messages, temperature=0.3)


def generate_image(prompt: str, quality: str) -> tuple[str, dict[str, Any], bool]:
    enhanced_prompt, mcp_used = enrich_image_prompt(prompt)
    if quality == "normal":
        url, usage = dashscope_text_to_image(enhanced_prompt)
        return url, usage, mcp_used

    hu = settings.huanapi
    base = str(hu.get("base_url") or "https://www.huanapi.com")
    image_key = str(hu.get("image_key") or "").strip()
    if not image_key:
        raise CreationConfigError("GPT 生图未配置（HUANAPI_IMAGE_KEY）")

    premium_model = str(hu.get("model_image_premium") or "gpt-image-2").strip()
    if premium_model != "gpt-image-2":
        logger.warning("huanapi.model_image_premium=%r，将使用官方模型名 gpt-image-2", premium_model)
    url, usage = huanapi_images(base, image_key, "gpt-image-2", enhanced_prompt)
    return url, usage, mcp_used
