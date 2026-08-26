"""创作/推荐侧候选归一与白名单排序的公共小工具。"""

from __future__ import annotations

from collections.abc import Callable, Iterable
from typing import Any, TypeVar

T = TypeVar("T")


def unique_clip(
    values: Iterable[Any],
    *,
    limit: int,
    max_len: int,
) -> list[str]:
    """去空白、截断、去重后限长列表。"""
    out: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value or "").strip()[:max_len]
        if not text or text in seen:
            continue
        seen.add(text)
        out.append(text)
        if len(out) >= limit:
            break
    return out


def dedupe_clip(
    items: list[dict[str, Any]] | None,
    *,
    key_field: str,
    fields: dict[str, int],
    limit: int = 200,
    alt_key_fields: tuple[str, ...] = (),
) -> list[dict[str, Any]]:
    """按主键去重并裁剪字段长度。fields 为字段名→最大长度。"""
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items or []:
        if not isinstance(item, dict):
            continue
        key = str(item.get(key_field) or "").strip()
        if not key:
            for alt in alt_key_fields:
                key = str(item.get(alt) or "").strip()
                if key:
                    break
        if not key or key in seen:
            continue
        seen.add(key)
        row: dict[str, Any] = {key_field: key[: fields.get(key_field, 128)]}
        for field_name, max_len in fields.items():
            if field_name == key_field:
                continue
            value = item.get(field_name)
            if isinstance(value, list):
                row[field_name] = [str(x)[:max_len] for x in value[:8]]
            else:
                row[field_name] = str(value or "")[:max_len]
        out.append(row)
        if len(out) >= limit:
            break
    return out


def filter_ranked(
    ranked: Iterable[T],
    allowed_ids: set[Any],
    *,
    key_fn: Callable[[T], Any],
    score_fn: Callable[[T], float],
    threshold: float,
    limit: int,
) -> list[T]:
    """白名单 + 分数阈值 + 去重 + 限长。"""
    result: list[T] = []
    seen: set[Any] = set()
    for item in sorted(ranked, key=score_fn, reverse=True):
        key = key_fn(item)
        if key not in allowed_ids or key in seen or score_fn(item) < threshold:
            continue
        seen.add(key)
        result.append(item)
        if len(result) >= limit:
            break
    return result
