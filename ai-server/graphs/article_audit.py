"""
帖子异步审核 LangGraph.

流程:
   START
     │
     ├─► validate_text  ─── reject ──► finalize (REJECTED)
     │       │ pass
     ├─► validate_images ── reject ──► finalize (REJECTED)
     │       │ pass (+ video_url)
     ├─► validate_video  ── reject ──► finalize (REJECTED)  [qwen3-vl-plus]
     │       │ pass
     ├─► summarize  ────────────────► finalize (APPROVED)
     │
     END

异常路径: 任意节点抛异常被 try/except 兜底,
state["final_status"]="AUDIT_ERROR", 直接路由到 finalize.

PostgresSaver 在每个节点完成后自动 checkpoint:
- thread_id = f"audit:{article_id}:{task_id}"
- 服务挂掉重启时, 该 task_id 在 Java 侧通过超时兜底 task 转 AUDIT_ERROR,
  Postgres 中残留的 checkpoint 仅供事后调试

输入: AuditState (含 title/content/cover_url/image_urls)
输出: AuditState (含 final_status / final_reason / summary)
"""
from __future__ import annotations

import concurrent.futures as cf
import logging
from typing import Any, Iterable

from langchain_core.messages import HumanMessage
from langgraph.graph import END, START, StateGraph

from clients.checkpoint import get_checkpointer
from clients.llm import text_llm, vision_llm, vision_llm_fallback
from config import settings
from graphs.prompts import (
    IMAGE_AUDIT_TEMPLATE,
    IMAGE_DESC_PROMPT,
    SUMMARY_TEMPLATE,
    TEXT_AUDIT_TEMPLATE,
)
from graphs.state import AuditState
from runtime.ai_runtime import AiRuntime
from utils import cache as semantic_cache
from utils.html import clean_html
from utils.image import fetch_image_bytes, to_data_url, validate_image_bytes
from utils.video_audit import audit_video_url

logger = logging.getLogger(__name__)


_FINAL_APPROVED = "APPROVED"
_FINAL_REJECTED = "REJECTED"
_FINAL_ERROR = "AUDIT_ERROR"
_runtime = AiRuntime()


# ────────────────────────────────────────────────────────────
# 节点实现
# ────────────────────────────────────────────────────────────
def _llm_text(prompt_inputs: dict, *, label: str, trace_id: str) -> str | None:
    """文本类 LLM 调用; 失败返回 None"""
    try:
        resp = _runtime.call_llm(
            lambda: (TEXT_AUDIT_TEMPLATE | text_llm()).invoke(prompt_inputs),
            trace_id=trace_id,
            model_name=str(settings.dashscope.get("model_text") or "qwen3.6-flash"),
        )
    except Exception:
        logger.exception("[graph:%s] LLM 调用失败", label)
        return None
    return _extract_text(resp)


def _extract_text(resp) -> str:
    content = getattr(resp, "content", resp)
    if isinstance(content, list) and content:
        first = content[0]
        text = first.get("text", "") if isinstance(first, dict) else str(first)
    elif isinstance(content, str):
        text = content
    else:
        text = str(content)
    return text.strip()


def node_validate_text(state: AuditState) -> AuditState:
    title = state.get("title", "") or ""
    content = state.get("content", "") or ""
    plain = clean_html(content)
    max_chars = int(settings.audit.get("text_audit_max_chars", 12000))
    if len(plain) > max_chars:
        plain = plain[:max_chars] + "…"

    new_state: AuditState = {"plain_text": plain}

    if not plain and not title.strip():
        # 空帖一律不允许
        new_state["text_result"] = {"allow": False, "reason": "标题与正文均为空"}
        return new_state

    # 缓存命中直接出
    cache_key_text = f"{title}\n{plain}"
    cached = semantic_cache.find_match(cache_key_text)
    if cached:
        new_state["text_result"] = cached
        return new_state

    raw = _llm_text(
        {"title": title, "text": plain or "(无正文)"},
        label="text",
        trace_id=str(state.get("task_id") or ""),
    )
    if raw is None:
        # LLM 失败 -> 走 AUDIT_ERROR; 这里设置一个错误占位, 由 route 路由到 finalize
        new_state["text_result"] = {"allow": False, "reason": "审核服务暂时不可用", "error": True}
        return new_state

    allowed = raw.upper() == "YES"
    msg = "OK" if allowed else raw
    result = {"allow": allowed, "msg": msg, "reason": "" if allowed else raw}
    semantic_cache.save(cache_key_text, result)
    new_state["text_result"] = result
    return new_state


def _describe_image(image_bytes: bytes, *, trace_id: str) -> str:
    fmt = validate_image_bytes(image_bytes)
    if not fmt:
        return ""
    data_url = to_data_url(image_bytes, fmt)
    try:
        resp = _runtime.call_llm(
            lambda: vision_llm().invoke([HumanMessage(content=[
                {"image": data_url},
                {"text": IMAGE_DESC_PROMPT},
            ])]),
            trace_id=trace_id,
            model_name=str(settings.dashscope.get("model_vision") or "qwen3-vl-flash"),
            fallback=lambda: vision_llm_fallback().invoke([HumanMessage(content=[
                {"image": data_url},
                {"text": IMAGE_DESC_PROMPT},
            ])]),
            fallback_model_name=str(
                settings.dashscope.get("model_vision_fallback") or "qwen3-vl-plus"
            ),
        )
    except Exception:
        logger.exception("[graph:image] 生成图片描述失败（含兜底）")
        return ""
    return _extract_text(resp)


def _audit_image_bytes(img_bytes: bytes, *, source: str = "", trace_id: str = "") -> dict:
    """对已下载的图片 bytes 做视觉描述 + 文本审核."""
    if not img_bytes:
        return {"url": source, "allow": False, "reason": "图片无法拉取或格式不支持"}
    desc = _describe_image(img_bytes, trace_id=trace_id)
    if not desc:
        return {"url": source, "allow": False, "reason": "图片识别失败"}
    try:
        resp = _runtime.call_llm(
            lambda: (IMAGE_AUDIT_TEMPLATE | text_llm()).invoke({"desc": desc}),
            trace_id=trace_id,
            model_name=str(settings.dashscope.get("model_text") or "qwen3.6-flash"),
        )
    except Exception:
        logger.exception("[graph:image] 审核 LLM 失败")
        return {"url": source, "allow": False, "reason": "图片审核服务异常", "error": True}
    text = _extract_text(resp)
    allowed = text.startswith("是")
    return {"url": source, "allow": allowed,
            "reason": "" if allowed else f"图片不合规({desc[:30]})"}


def _audit_single_image(url: str, *, trace_id: str) -> dict:
    """拉远程图 -> 视觉描述 -> 文本审核"""
    if not url:
        return {"url": url, "allow": True, "reason": "skip empty"}
    img_bytes = fetch_image_bytes(url)
    if not img_bytes:
        return {"url": url, "allow": False, "reason": "图片无法拉取或格式不支持"}
    return _audit_image_bytes(img_bytes, source=url, trace_id=trace_id)


def node_validate_video(state: AuditState) -> AuditState:
    url = (state.get("video_url") or "").strip()
    if not url:
        return {"video_result": {"allow": True, "reason": "skip empty"}}
    result = audit_video_url(
        url,
        image_audit_fn=lambda b: _audit_image_bytes(
            b,
            source=url,
            trace_id=str(state.get("task_id") or ""),
        ),
    )
    return {"video_result": result}


def node_validate_images(state: AuditState) -> AuditState:
    urls: list[str] = []
    cover = state.get("cover_url")
    if cover:
        urls.append(cover)
    urls.extend([u for u in (state.get("image_urls") or []) if u])

    if not urls:
        return {"image_results": []}

    max_workers = int(settings.audit.get("max_image_workers", 3))
    results: list[dict] = []
    with cf.ThreadPoolExecutor(max_workers=max_workers) as pool:
        for r in pool.map(
            lambda url: _audit_single_image(url, trace_id=str(state.get("task_id") or "")),
            urls,
        ):
            results.append(r)
    return {"image_results": results}


def node_summarize(state: AuditState) -> AuditState:
    plain = state.get("plain_text") or clean_html(state.get("content"))
    min_len = int(settings.audit.get("summary_min_len", 50))
    if not plain or len(plain) < min_len:
        return {"summary": plain[:100] if plain else ""}
    try:
        resp = _runtime.call_llm(
            lambda: (SUMMARY_TEMPLATE | text_llm(temperature=0.3)).invoke({"text": plain}),
            trace_id=str(state.get("task_id") or ""),
            model_name=str(settings.dashscope.get("model_text") or "qwen3.6-flash"),
        )
    except Exception:
        logger.exception("[graph:summarize] 摘要 LLM 失败")
        return {"summary": ""}
    return {"summary": _extract_text(resp)}


def node_finalize(state: AuditState) -> AuditState:
    # 已经被 route_* 节点定下 final_status
    return state


# ────────────────────────────────────────────────────────────
# 路由
# ────────────────────────────────────────────────────────────
def route_after_text(state: AuditState) -> str:
    tr = state.get("text_result") or {}
    if tr.get("error"):
        return "error"
    if not tr.get("allow"):
        return "reject"
    return "pass"


def route_after_images(state: AuditState) -> str:
    results: Iterable[dict] = state.get("image_results") or []
    for r in results:
        if r.get("error"):
            return "error"
        if not r.get("allow"):
            return "reject"
    if (state.get("video_url") or "").strip():
        return "video"
    return "pass"


def route_after_video(state: AuditState) -> str:
    vr = state.get("video_result") or {}
    if vr.get("error"):
        return "error"
    if not vr.get("allow"):
        return "reject"
    return "pass"


def node_to_rejected_text(state: AuditState) -> AuditState:
    reason = (state.get("text_result") or {}).get("reason", "内容违规")
    return {"final_status": _FINAL_REJECTED, "final_reason": reason}


def node_to_rejected_video(state: AuditState) -> AuditState:
    reason = (state.get("video_result") or {}).get("reason", "视频违规")
    return {"final_status": _FINAL_REJECTED, "final_reason": reason}


def node_to_rejected_image(state: AuditState) -> AuditState:
    reason = "图片违规"
    for r in state.get("image_results") or []:
        if not r.get("allow"):
            reason = r.get("reason", "图片违规")
            break
    return {"final_status": _FINAL_REJECTED, "final_reason": reason}


def node_to_error(state: AuditState) -> AuditState:
    text_err = (state.get("text_result") or {}).get("error")
    if text_err:
        reason = (state.get("text_result") or {}).get("reason", "审核服务异常")
    else:
        reason = "审核服务异常"
        for r in state.get("image_results") or []:
            if r.get("error"):
                reason = r.get("reason", "审核服务异常")
                break
        if not (state.get("text_result") or {}).get("error"):
            vr = state.get("video_result") or {}
            if vr.get("error"):
                reason = vr.get("reason", "视频审核服务异常")
    return {"final_status": _FINAL_ERROR, "final_reason": reason}


def node_to_approved(state: AuditState) -> AuditState:
    return {"final_status": _FINAL_APPROVED,
            "final_reason": "审核通过",
            "summary": state.get("summary", "")}


# ────────────────────────────────────────────────────────────
# 图构建
# ────────────────────────────────────────────────────────────
def build_graph() -> StateGraph:
    g = StateGraph(AuditState)
    g.add_node("validate_text", node_validate_text)
    g.add_node("validate_images", node_validate_images)
    g.add_node("validate_video", node_validate_video)
    g.add_node("summarize", node_summarize)
    g.add_node("reject_text", node_to_rejected_text)
    g.add_node("reject_image", node_to_rejected_image)
    g.add_node("reject_video", node_to_rejected_video)
    g.add_node("error", node_to_error)
    g.add_node("approved", node_to_approved)
    g.add_node("finalize", node_finalize)

    g.add_edge(START, "validate_text")
    g.add_conditional_edges("validate_text", route_after_text, {
        "pass": "validate_images",
        "reject": "reject_text",
        "error": "error",
    })
    g.add_conditional_edges("validate_images", route_after_images, {
        "pass": "summarize",
        "video": "validate_video",
        "reject": "reject_image",
        "error": "error",
    })
    g.add_conditional_edges("validate_video", route_after_video, {
        "pass": "summarize",
        "reject": "reject_video",
        "error": "error",
    })
    g.add_edge("summarize", "approved")
    g.add_edge("approved", "finalize")
    g.add_edge("reject_text", "finalize")
    g.add_edge("reject_image", "finalize")
    g.add_edge("reject_video", "finalize")
    g.add_edge("error", "finalize")
    g.add_edge("finalize", END)
    return g


_compiled = None


def get_audit_app() -> Any:
    """编译图 + 绑定 PostgresSaver; 进程级缓存"""
    global _compiled
    if _compiled is not None:
        return _compiled
    checkpointer = get_checkpointer()
    _compiled = build_graph().compile(checkpointer=checkpointer)
    logger.info("[graph] article_audit 图已编译")
    return _compiled


def run_audit(task: dict) -> dict:
    """
    跑一次审核, 返回结果 dict(可直接序列化投递到 forum.audit.result):
      {
        "taskId": str,
        "articleId": int,
        "userId": int,
        "finalStatus": str,
        "finalReason": str,
        "title": str,
        "summary": str,
        "finishedAt": int(ms)
      }
    """
    import time

    app = get_audit_app()
    state: AuditState = {
        "task_id": task["taskId"],
        "article_id": task["articleId"],
        "user_id": task.get("userId"),
        "title": task.get("title", ""),
        "content": task.get("content", ""),
        "cover_url": task.get("coverUrl"),
        "image_urls": task.get("imageUrls") or [],
        "video_url": task.get("videoUrl"),
        "submitted_at": task.get("submittedAt", 0),
    }
    cfg = {"configurable": {"thread_id": f"audit:{task['articleId']}:{task['taskId']}"}}
    try:
        final_state = app.invoke(state, config=cfg)
    except Exception:
        logger.exception("[graph] 审核流程执行抛异常 taskId=%s", task["taskId"])
        return {
            "taskId": task["taskId"],
            "articleId": task["articleId"],
            "userId": task.get("userId"),
            "finalStatus": _FINAL_ERROR,
            "finalReason": "审核流程内部异常",
            "title": task.get("title", ""),
            "summary": "",
            "finishedAt": int(time.time() * 1000),
        }
    return {
        "taskId": task["taskId"],
        "articleId": task["articleId"],
        "userId": task.get("userId"),
        "finalStatus": final_state.get("final_status", _FINAL_ERROR),
        "finalReason": final_state.get("final_reason", ""),
        "title": task.get("title", ""),
        "summary": final_state.get("summary", "") or "",
        "finishedAt": int(time.time() * 1000),
    }
