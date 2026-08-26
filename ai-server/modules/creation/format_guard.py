"""润色正文中的不可变媒体、代码和链接保护。"""

from __future__ import annotations

from dataclasses import dataclass
from html.parser import HTMLParser
import re


_TOKEN_PREFIX = "__AI_KEEP_"
_MARKDOWN_PATTERNS = (
    re.compile(r"```[\s\S]*?```"),
    re.compile(r"`[^`\n]+`"),
    re.compile(r"!\[[^\]]*\]\([^\n)]+\)"),
    re.compile(r"\[[^\]]+\]\([^\n)]+\)"),
    re.compile(r"^(?:#{1,6}|>|\s*(?:[-+*]|\d+[.)]))[ \t]+", re.MULTILINE),
)
_RICH_LOCKED_PATTERN = re.compile(
    r"<(?P<tag>[a-z][\w:-]*)\b(?=[^>]*\bcontenteditable\s*=\s*['\"]?false['\"]?)[^>]*>"
    r"[\s\S]*?</(?P=tag)>",
    re.IGNORECASE,
)
_RICH_BLOCK_PATTERN = re.compile(
    r"<(?:pre|code|video|audio|iframe)\b[^>]*>[\s\S]*?</(?:pre|code|video|audio|iframe)>"
    r"|<(?:img|source)\b[^>]*?/?>",
    re.IGNORECASE,
)
_RICH_URL_PATTERN = re.compile(r"(?P<prefix>\b(?:href|src)\s*=\s*['\"])(?P<url>[^'\"]+)(?P<suffix>['\"])", re.IGNORECASE)


@dataclass(frozen=True)
class ProtectedContent:
    text: str
    values: dict[str, str]

    def restore(self, candidate: str) -> str | None:
        restored = str(candidate or "")
        for token, value in self.values.items():
            if restored.count(token) != 1:
                return None
            restored = restored.replace(token, value)
        if _TOKEN_PREFIX in restored:
            return None
        return restored.strip()


def protect_content(content: str, editor_mode: str) -> ProtectedContent:
    values: dict[str, str] = {}

    def stash(value: str) -> str:
        token = f"{_TOKEN_PREFIX}{len(values):04d}__"
        values[token] = value
        return token

    protected = str(content or "")
    if editor_mode == "markdown":
        for pattern in _MARKDOWN_PATTERNS:
            protected = pattern.sub(lambda match: stash(match.group(0)), protected)
    else:
        protected = _RICH_LOCKED_PATTERN.sub(lambda match: stash(match.group(0)), protected)
        protected = _RICH_BLOCK_PATTERN.sub(lambda match: stash(match.group(0)), protected)

        def replace_url(match: re.Match[str]) -> str:
            return f"{match.group('prefix')}{stash(match.group('url'))}{match.group('suffix')}"

        protected = _RICH_URL_PATTERN.sub(replace_url, protected)
    return ProtectedContent(text=protected, values=values)


def strip_outer_code_fence(text: str) -> str:
    normalized = str(text or "").strip()
    match = re.fullmatch(r"```(?:markdown|md|html)?\s*\n?([\s\S]*?)\n?```", normalized, re.IGNORECASE)
    return match.group(1).strip() if match else normalized


def is_valid_polished_content(content: str, editor_mode: str) -> bool:
    candidate = strip_outer_code_fence(content)
    if not candidate:
        return False
    if editor_mode == "markdown":
        return True
    if not re.search(r"</?(?:p|div|h[1-6]|ul|ol|li|blockquote|table|pre|br)\b", candidate, re.IGNORECASE):
        return False
    parser = _TolerantHtmlParser()
    try:
        parser.feed(candidate)
        parser.close()
    except Exception:
        return False
    return not parser.unclosed_tags


class _TolerantHtmlParser(HTMLParser):
    _VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.unclosed_tags: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() not in self._VOID:
            self.unclosed_tags.append(tag.lower())

    def handle_endtag(self, tag: str) -> None:
        normalized = tag.lower()
        if normalized in self.unclosed_tags:
            index = len(self.unclosed_tags) - 1 - self.unclosed_tags[::-1].index(normalized)
            del self.unclosed_tags[index:]
