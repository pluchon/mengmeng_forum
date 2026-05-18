"""HTML 工具"""
from __future__ import annotations

import re

HTML_TAG_REGEX = re.compile(r"<[^>]+>")


def clean_html(text: str | None) -> str:
    if not text:
        return ""
    return HTML_TAG_REGEX.sub("", text).strip()
