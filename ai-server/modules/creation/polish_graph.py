"""帖子正文自适应润色 LangGraph 子图。"""

from __future__ import annotations

from typing import Annotated, Any, Literal
import json
import logging
import operator
import re

from langgraph.graph import END, START, StateGraph
from langgraph.types import Send
from pydantic import BaseModel, Field, ValidationError
from typing_extensions import TypedDict

from clients.dashscope_chat_client import dashscope_chat_completion, json_chat_completion
from clients.llm import deep_model_name, flash_model_name
from modules.creation.format_guard import (
    ProtectedContent,
    is_valid_polished_content,
    protect_content,
    strip_outer_code_fence,
)
from modules.creation.usage import aggregate_usage, usage_item
from runtime.graph_run import invoke_with_fanout_limit

logger = logging.getLogger(__name__)

_PASS_SCORE = 70
_DEFAULT_STRATEGIES = ("自然表达", "清晰结构", "保持语气", "简洁得体")
_AI_PHRASES = re.compile(r"首先|其次|最后|综上所述|值得注意的是|总而言之")


class PolishPlan(BaseModel):
    complexity: Literal["simple", "medium", "complex"]
    worker_count: int = Field(ge=1, le=4)
    strategies: list[str] = Field(min_length=1, max_length=4)
    needs_deep: bool = False
    confidence: float = Field(ge=0, le=1)
    reason: str = ""


class PolishEvaluation(BaseModel):
    selected_index: int = Field(ge=0)
    selected_score: int = Field(ge=0, le=100)
    acceptable: bool
    needs_refine: bool
    feedback: str = ""


class PolishState(TypedDict, total=False):
    title: str
    content: str
    editor_mode: str
    protected: ProtectedContent
    plan: PolishPlan
    worker_tasks: list[dict[str, Any]]
    candidates: Annotated[list[dict[str, Any]], operator.add]
    usages: Annotated[list[dict[str, Any]], operator.add]
    selected: str
    evaluation: PolishEvaluation
    final: str
    deep_used: bool
    error: str


class PolishWorkerState(TypedDict, total=False):
    title: str
    protected_text: str
    editor_mode: str
    strategy: str
    candidate_index: int
    candidates: Annotated[list[dict[str, Any]], operator.add]
    usages: Annotated[list[dict[str, Any]], operator.add]


def run_polish_graph(title: str, content: str, editor_mode: str) -> dict[str, Any]:
    state = invoke_with_fanout_limit(_POLISH_GRAPH, {
        "title": title,
        "content": content,
        "editor_mode": editor_mode,
        "candidates": [],
        "usages": [],
        "deep_used": False,
    })
    final = str(state.get("final") or "").strip()
    if not final:
        raise ValueError(str(state.get("error") or "AI 未返回有效润色结果"))
    usage = aggregate_usage(state.get("usages") or [])
    return {
        "content": final,
        "usage": usage,
        "route": state.get("plan").complexity if state.get("plan") else "simple",
        "candidateCount": len(state.get("candidates") or []),
        "deepUsed": bool(state.get("deep_used")),
    }


def node_protect(state: PolishState) -> dict[str, Any]:
    return {"protected": protect_content(state.get("content", ""), state.get("editor_mode", "rich"))}


def node_analyze(state: PolishState) -> dict[str, Any]:
    flash = _flash_model()
    protected = state["protected"]
    prompt = (
        "请以 JSON 判断这篇帖子润色的语义复杂度。不能按字数直接判断，要综合事实密度、逻辑层次、"
        "专业术语、表达歧义、结构混乱程度和语气一致性。simple 使用1个worker，medium使用2个，"
        "complex使用3到4个。若Flash难以可靠评审或精修，needs_deep=true。"
        "JSON字段必须为 complexity、worker_count、strategies、needs_deep、confidence、reason。"
        f"\n编辑器格式：{state.get('editor_mode')}\n标题：{state.get('title', '')}\n正文：\n{protected.text[:32000]}"
    )
    try:
        raw, usage = _json_completion(flash, prompt, temperature=0.1)
        plan = PolishPlan.model_validate_json(raw)
        plan = _normalize_plan(plan)
        return {
            "plan": plan,
            "worker_tasks": _build_worker_tasks(plan),
            "usages": [usage_item(usage, "polish_analyze")],
        }
    except Exception:
        logger.exception("正文润色复杂度分析失败，降级为单 Flash worker")
        fallback = PolishPlan(
            complexity="simple",
            worker_count=1,
            strategies=[_DEFAULT_STRATEGIES[0]],
            needs_deep=False,
            confidence=0,
            reason="analysis_fallback",
        )
        return {"plan": fallback, "worker_tasks": _build_worker_tasks(fallback)}


def assign_workers(state: PolishState) -> list[Send]:
    protected = state["protected"]
    return [
        Send("polish_worker", {
            "title": state.get("title", ""),
            "protected_text": protected.text,
            "editor_mode": state.get("editor_mode", "rich"),
            "strategy": task["strategy"],
            "candidate_index": task["candidate_index"],
            "candidates": [],
            "usages": [],
        })
        for task in state.get("worker_tasks", [])[:4]
    ]


def node_polish_worker(state: PolishWorkerState) -> dict[str, Any]:
    editor_mode = state.get("editor_mode", "rich")
    format_rule = (
        "输出完整 Markdown，保留标题、列表、引用及占位符，不要包裹在代码围栏中。"
        if editor_mode == "markdown"
        else "输出完整富文本 HTML，保留原标签层次和所有占位符，不要输出 Markdown 或解释。"
    )
    system = (
        "你是中文论坛编辑。只润色用户原文，不新增事实、观点、经历、结论或专业判断。"
        "使用自然的大白话，得体但不端着，保留作者原有口吻和情绪。"
        "避免机械套用‘首先、其次、综上所述、值得注意的是’，避免无意义拔高和AI腔。"
        "所有 __AI_KEEP_XXXX__ 占位符必须原样保留且各出现一次。" + format_rule
    )
    messages = [
        {"role": "system", "content": system},
        {"role": "user", "content": (
            f"本次侧重：{state.get('strategy', '自然表达')}\n"
            f"标题：{state.get('title', '')}\n正文：\n{state.get('protected_text', '')[:32000]}"
        )},
    ]
    try:
        text, usage = dashscope_chat_completion(_flash_model(), messages, temperature=0.42, timeout=180)
        return {
            "candidates": [{
                "index": int(state.get("candidate_index", 0)),
                "strategy": state.get("strategy", ""),
                "content": strip_outer_code_fence(text),
            }],
            "usages": [usage_item(usage, f"polish_worker_{int(state.get('candidate_index', 0))}")],
        }
    except Exception as exc:
        logger.warning("正文润色 worker 失败 index=%s: %s", state.get("candidate_index"), exc)
        return {"candidates": [{
            "index": int(state.get("candidate_index", 0)),
            "strategy": state.get("strategy", ""),
            "content": "",
            "error": "worker_failed",
        }]}


def node_evaluate(state: PolishState) -> dict[str, Any]:
    valid = _restore_valid_candidates(state)
    if not valid:
        return {"error": "所有润色候选均未通过格式校验"}

    plan = state["plan"]
    evaluation_model = _deep_model() if plan.needs_deep else _flash_model()
    compact_candidates = [
        {"index": item["index"], "strategy": item["strategy"], "content": item["restored"]}
        for item in valid
    ]
    prompt = (
        "请以 JSON 评估润色候选。标准为：不改变事实和原意、语言自然无AI腔、清晰易读、"
        "与原格式一致。一般水准即可，70分算合格。选择一个最佳候选；若都不够好则 needs_refine=true。"
        "JSON字段必须为 selected_index、selected_score、acceptable、needs_refine、feedback。"
        f"\n原文：\n{state.get('content', '')[:32000]}\n候选：\n"
        f"{json.dumps(compact_candidates, ensure_ascii=False)}"
    )
    deep_used = plan.needs_deep
    try:
        raw, usage = _json_completion(evaluation_model, prompt, temperature=0.1)
        evaluation = PolishEvaluation.model_validate_json(raw)
        selected = _candidate_by_index(valid, evaluation.selected_index) or _best_deterministic(valid, state)
        acceptable = evaluation.acceptable and evaluation.selected_score >= _PASS_SCORE
        normalized = evaluation.model_copy(update={
            "selected_index": selected["index"],
            "acceptable": acceptable,
            "needs_refine": evaluation.needs_refine or not acceptable,
        })
        result: dict[str, Any] = {
            "selected": selected["restored"],
            "evaluation": normalized,
            "deep_used": deep_used,
            "usages": [usage_item(usage, "polish_evaluate")],
        }
        if acceptable and not normalized.needs_refine:
            result["final"] = selected["restored"]
        return result
    except (ValidationError, ValueError, TypeError):
        logger.exception("正文润色评估结果无效，使用确定性检查兜底")
    except Exception:
        logger.exception("正文润色评估失败，使用确定性检查兜底")

    selected = _best_deterministic(valid, state)
    return {
        "selected": selected["restored"],
        "evaluation": PolishEvaluation(
            selected_index=selected["index"],
            selected_score=_deterministic_score(selected["restored"], state.get("content", "")),
            acceptable=True,
            needs_refine=False,
            feedback="评估模型不可用，已采用格式与自然度检查最优候选",
        ),
        "final": selected["restored"],
        "deep_used": deep_used,
    }


def route_after_evaluate(state: PolishState) -> str:
    if state.get("final") or state.get("error"):
        return "done"
    evaluation = state.get("evaluation")
    return "refine" if evaluation and evaluation.needs_refine else "done"


def node_refine(state: PolishState) -> dict[str, Any]:
    plan = state["plan"]
    model = _deep_model() if plan.needs_deep else _flash_model()
    editor_mode = state.get("editor_mode", "rich")
    selected_protected = protect_content(state.get("selected", ""), editor_mode)
    prompt = (
        "根据评审意见只做一次克制修正。不得新增事实，不要解释，不要输出开场语。"
        "保持自然大白话，所有 __AI_KEEP_XXXX__ 占位符必须原样保留。"
        f"\n格式：{editor_mode}\n评审意见：{state.get('evaluation').feedback if state.get('evaluation') else ''}"
        f"\n待修正文：\n{selected_protected.text[:32000]}"
    )
    try:
        text, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": "你是中文论坛正文润色复核编辑。"}, {"role": "user", "content": prompt}],
            temperature=0.25,
            timeout=180,
        )
        restored = selected_protected.restore(strip_outer_code_fence(text))
        if restored and is_valid_polished_content(restored, editor_mode):
            return {
                "final": restored,
                "deep_used": plan.needs_deep,
                "usages": [usage_item(usage, "polish_refine")],
            }
    except Exception:
        logger.exception("正文润色单次精修失败，回退评审选中候选")
    selected = state.get("selected", "").strip()
    if selected:
        return {"final": selected, "deep_used": plan.needs_deep}
    return {"error": "润色精修未产生有效正文"}


def _json_completion(model: str, prompt: str, *, temperature: float) -> tuple[str, dict[str, Any]]:
    return json_chat_completion(
        model,
        [
            {"role": "system", "content": "你是受控工作流节点。必须只输出一个合法 JSON 对象。"},
            {"role": "user", "content": prompt},
        ],
        temperature=temperature,
        timeout=180,
    )


def _normalize_plan(plan: PolishPlan) -> PolishPlan:
    expected = {"simple": 1, "medium": 2, "complex": max(3, min(4, plan.worker_count))}[plan.complexity]
    strategies = [item.strip()[:40] for item in plan.strategies if item and item.strip()]
    for fallback in _DEFAULT_STRATEGIES:
        if len(strategies) >= expected:
            break
        if fallback not in strategies:
            strategies.append(fallback)
    return plan.model_copy(update={
        "worker_count": expected,
        "strategies": strategies[:expected],
        "needs_deep": plan.needs_deep or plan.confidence < 0.65,
    })


def _build_worker_tasks(plan: PolishPlan) -> list[dict[str, Any]]:
    return [
        {"candidate_index": index, "strategy": plan.strategies[index]}
        for index in range(min(plan.worker_count, len(plan.strategies), 4))
    ]


def _restore_valid_candidates(state: PolishState) -> list[dict[str, Any]]:
    protected = state["protected"]
    editor_mode = state.get("editor_mode", "rich")
    restored: list[dict[str, Any]] = []
    for item in state.get("candidates", []):
        value = protected.restore(strip_outer_code_fence(str(item.get("content") or "")))
        if value and is_valid_polished_content(value, editor_mode):
            restored.append({**item, "restored": value})
    return restored


def _candidate_by_index(candidates: list[dict[str, Any]], index: int) -> dict[str, Any] | None:
    return next((item for item in candidates if item["index"] == index), None)


def _best_deterministic(candidates: list[dict[str, Any]], state: PolishState) -> dict[str, Any]:
    original = state.get("content", "")
    return max(candidates, key=lambda item: _deterministic_score(item["restored"], original))


def _deterministic_score(candidate: str, original: str) -> int:
    original_len = max(1, len(original))
    ratio = len(candidate) / original_len
    length_penalty = min(25, int(abs(1 - ratio) * 30))
    phrase_penalty = min(20, len(_AI_PHRASES.findall(candidate)) * 4)
    return max(0, 100 - length_penalty - phrase_penalty)


def _flash_model() -> str:
    return flash_model_name()


def _deep_model() -> str:
    return deep_model_name()


_builder = StateGraph(PolishState)
_builder.add_node("protect", node_protect)
_builder.add_node("analyze", node_analyze)
_builder.add_node("polish_worker", node_polish_worker)
_builder.add_node("evaluate", node_evaluate)
_builder.add_node("refine", node_refine)
_builder.add_edge(START, "protect")
_builder.add_edge("protect", "analyze")
_builder.add_conditional_edges("analyze", assign_workers, ["polish_worker"])
_builder.add_edge("polish_worker", "evaluate")
_builder.add_conditional_edges("evaluate", route_after_evaluate, {"refine": "refine", "done": END})
_builder.add_edge("refine", END)
_POLISH_GRAPH = _builder.compile()
