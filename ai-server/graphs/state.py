"""
LangGraph 审核流程的 State 定义.
TypedDict 比 BaseModel 更轻量, langgraph 推荐用法.
"""
from __future__ import annotations

from typing import Any, TypedDict


class AuditState(TypedDict, total=False):
    # 输入
    task_id: str
    article_id: int
    user_id: int
    title: str
    content: str
    cover_url: str | None
    image_urls: list[str]
    submitted_at: int

    # 中间产物
    plain_text: str
    text_result: dict[str, Any]            # {"allow": bool, "reason": str}
    image_results: list[dict[str, Any]]    # [{"url", "allow", "reason"}]
    summary: str

    # 终态
    final_status: str   # APPROVED / REJECTED / AUDIT_ERROR
    final_reason: str
