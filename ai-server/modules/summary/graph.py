"""帖子详情页自适应总结 LangGraph 子图。"""

from __future__ import annotations

import json
import logging
import operator
import re
from typing import Annotated, Any, Literal

from langgraph.graph import END, START, StateGraph
from langgraph.types import Send
from pydantic import BaseModel, Field
from typing_extensions import TypedDict

from clients.dashscope_chat_client import dashscope_chat_completion, json_chat_completion
from clients.llm import deep_model_name, flash_model_name
from mcp.registry import invoke_tool
from modules.creation.usage import aggregate_usage, usage_item
from runtime.graph_run import invoke_with_fanout_limit

logger = logging.getLogger(__name__)

_AI_PHRASES = re.compile(r"首先|其次|最后|综上所述|总而言之|值得注意的是|由此可见")
_STRATEGIES = ("忠实概括", "自然表达", "信息完整")


class SummaryPlan(BaseModel):
    complexity: Literal["simple", "medium", "complex"]
    worker_count: int = Field(ge=1, le=3)
    confidence: float = Field(ge=0, le=1)
    needs_deep: bool = False
    needs_search: bool = False
    unknown_terms: list[str] = Field(default_factory=list, max_length=5)
    search_query: str = Field(default="", max_length=120)
    target_chars: int = Field(default=100, ge=40, le=260)


class SummaryEvaluation(BaseModel):
    selected_index: int = Field(ge=0)
    score: int = Field(ge=0, le=100)
    acceptable: bool
    needs_refine: bool = False
    feedback: str = ""


class SummaryState(TypedDict, total=False):
    title: str
    content: str
    plan: SummaryPlan
    worker_tasks: list[dict[str, Any]]
    search_context: str
    candidates: Annotated[list[dict[str, Any]], operator.add]
    usages: Annotated[list[dict[str, Any]], operator.add]
    selected: str
    evaluation: SummaryEvaluation
    final: str
    deep_used: bool
    mcp_used: bool
    error: str


class SummaryWorkerState(TypedDict, total=False):
    title: str
    content: str
    strategy: str
    candidate_index: int
    target_chars: int
    search_context: str
    candidates: Annotated[list[dict[str, Any]], operator.add]
    usages: Annotated[list[dict[str, Any]], operator.add]


def run_summary_graph(title: str, content: str) -> dict[str, Any]:
    """执行帖子总结图并返回最终结果及实际模型用量。"""

    state = invoke_with_fanout_limit(_SUMMARY_GRAPH, {
        "title": title,
        "content": content,
        "candidates": [],
        "usages": [],
        "deep_used": False,
        "mcp_used": False,
    })
    final = _normalize_summary(str(state.get("final") or ""))
    if not final:
        raise ValueError(str(state.get("error") or "AI 未返回有效总结"))
    plan = state.get("plan")
    return {
        "summary": final,
        "route": plan.complexity if plan else "simple",
        "candidateCount": len([item for item in state.get("candidates", []) if item.get("content")]),
        "deepUsed": bool(state.get("deep_used")),
        "mcpUsed": bool(state.get("mcp_used")),
        "usage": aggregate_usage(state.get("usages") or []),
    }


def node_analyze(state: SummaryState) -> dict[str, Any]:
    prompt = (
        "请只输出JSON，判断论坛帖子总结难度。不能只按字数，要综合事实密度、逻辑层次、专业术语、"
        "观点冲突和歧义。simple使用1个worker，medium使用2个，complex使用3个。"
        "只有陌生名词确实影响正文理解时needs_search=true；Flash把握不足时needs_deep=true。"
        "target_chars在40到260之间，内容简单就短，信息密集才适当增加。"
        "字段：complexity、worker_count、confidence、needs_deep、needs_search、unknown_terms、"
        "search_query、target_chars。\n"
        f"标题：{state.get('title', '')[:200]}\n正文：{state.get('content', '')[:32000]}"
    )
    try:
        raw, usage = _json_completion(_flash_model(), prompt)
        plan = _normalize_plan(SummaryPlan.model_validate_json(raw))
        return {
            "plan": plan,
            "worker_tasks": _worker_tasks(plan),
            "usages": [usage_item(usage, "summary_analyze")],
        }
    except Exception:
        logger.exception("帖子总结复杂度分析失败，降级为单 Flash worker")
        fallback = SummaryPlan(
            complexity="simple",
            worker_count=1,
            confidence=0,
            target_chars=_fallback_target_chars(state.get("content", "")),
        )
        return {"plan": fallback, "worker_tasks": _worker_tasks(fallback)}


def node_search(state: SummaryState) -> dict[str, Any]:
    plan = state["plan"]
    if not plan.needs_search or not plan.search_query.strip():
        return {"search_context": "", "mcp_used": False}
    try:
        context = invoke_tool("tavily_search", {
            "query": plan.search_query.strip(),
            "max_results": 3,
            "search_depth": "basic",
        })
        return {"search_context": str(context or "")[:3500], "mcp_used": True}
    except Exception:
        logger.warning("帖子总结辅助检索失败，继续仅依据正文总结", exc_info=True)
        return {"search_context": "", "mcp_used": False}


def assign_workers(state: SummaryState) -> list[Send]:
    plan = state["plan"]
    return [
        Send("summary_worker", {
            "title": state.get("title", ""),
            "content": state.get("content", ""),
            "strategy": task["strategy"],
            "candidate_index": task["candidate_index"],
            "target_chars": plan.target_chars,
            "search_context": state.get("search_context", ""),
            "candidates": [],
            "usages": [],
        })
        for task in state.get("worker_tasks", [])[:3]
    ]


def node_summary_worker(state: SummaryWorkerState) -> dict[str, Any]:
    system = (
        "你是中文论坛的真人编辑。只总结正文真实表达的内容，不扩写、不评价作者、不编造事实。"
        "用自然、有活力的大白话，不写标题、列表、开场白或总结方法，禁止使用‘首先、其次、"
        "综上所述、由此可见’等结构性AI语言。输出一至两段正文总结。"
    )
    context = state.get("search_context", "")
    context_rule = (
        "联网资料仅用于理解陌生名词，不得把正文没有表达的资料写进总结。\n辅助资料：" + context
        if context else ""
    )
    prompt = (
        f"侧重：{state.get('strategy')}\n目标长度约{state.get('target_chars', 100)}个汉字。\n"
        f"标题：{state.get('title', '')[:200]}\n正文：{state.get('content', '')[:32000]}\n{context_rule}"
    )
    try:
        text, usage = dashscope_chat_completion(
            _flash_model(),
            [{"role": "system", "content": system}, {"role": "user", "content": prompt}],
            temperature=0.35,
            timeout=45,
        )
        return {
            "candidates": [{
                "index": int(state.get("candidate_index", 0)),
                "content": _normalize_summary(text),
            }],
            "usages": [usage_item(usage, f"summary_worker_{state.get('candidate_index', 0)}")],
        }
    except Exception:
        logger.warning("帖子总结 worker 失败 index=%s", state.get("candidate_index"), exc_info=True)
        return {"candidates": [{"index": int(state.get("candidate_index", 0)), "content": ""}]}


def node_evaluate(state: SummaryState) -> dict[str, Any]:
    candidates = [item for item in state.get("candidates", []) if _valid_summary(item.get("content", ""))]
    if not candidates:
        return {"error": "所有总结候选均无效"}
    plan = state["plan"]
    model = _deep_model() if plan.needs_deep else _flash_model()
    prompt = (
        "只输出JSON，评估候选是否忠于正文、是否抓住重点、是否自然无AI腔、长度是否得体。"
        "70分即为可用。字段：selected_index、score、acceptable、needs_refine、feedback。\n"
        f"正文：{state.get('content', '')[:26000]}\n候选："
        f"{json.dumps(candidates, ensure_ascii=False)}"
    )
    try:
        raw, usage = _json_completion(model, prompt)
        evaluation = SummaryEvaluation.model_validate_json(raw)
        selected = next(
            (item for item in candidates if item["index"] == evaluation.selected_index),
            _best_candidate(candidates, plan.target_chars),
        )
        acceptable = evaluation.acceptable and evaluation.score >= 70
        normalized = evaluation.model_copy(update={
            "selected_index": selected["index"],
            "acceptable": acceptable,
            "needs_refine": evaluation.needs_refine or not acceptable,
        })
        result: dict[str, Any] = {
            "selected": selected["content"],
            "evaluation": normalized,
            "deep_used": plan.needs_deep,
            "usages": [usage_item(usage, "summary_evaluate")],
        }
        if acceptable and not normalized.needs_refine:
            result["final"] = selected["content"]
        return result
    except Exception:
        logger.exception("帖子总结评估失败，采用确定性兜底")
        selected = _best_candidate(candidates, plan.target_chars)
        return {
            "selected": selected["content"],
            "final": selected["content"],
            "deep_used": plan.needs_deep,
        }


def route_after_evaluate(state: SummaryState) -> str:
    if state.get("final") or state.get("error"):
        return "done"
    return "refine"


def node_refine(state: SummaryState) -> dict[str, Any]:
    plan = state["plan"]
    model = _deep_model() if plan.needs_deep or plan.complexity == "complex" else _flash_model()
    feedback = state.get("evaluation").feedback if state.get("evaluation") else "表达不够自然"
    prompt = (
        "只对已有总结做一次克制修正。必须忠于正文，不新增事实，不写解释、标题或列表，"
        "使用自然大白话并去掉AI腔。\n"
        f"目标长度约{plan.target_chars}字\n评审意见：{feedback}\n正文："
        f"{state.get('content', '')[:24000]}\n待修总结：{state.get('selected', '')}"
    )
    try:
        text, usage = dashscope_chat_completion(
            model,
            [{"role": "system", "content": "你是论坛总结复核编辑。"}, {"role": "user", "content": prompt}],
            temperature=0.25,
            timeout=45,
        )
        final = _normalize_summary(text)
        if _valid_summary(final):
            return {
                "final": final,
                "deep_used": model == _deep_model(),
                "usages": [usage_item(usage, "summary_refine")],
            }
    except Exception:
        logger.exception("帖子总结精修失败，回退评审候选")
    selected = _normalize_summary(state.get("selected", ""))
    return {"final": selected} if selected else {"error": "总结精修未产生有效内容"}


def _json_completion(model: str, prompt: str) -> tuple[str, dict[str, Any]]:
    return json_chat_completion(
        model,
        [
            {"role": "system", "content": "你是受控工作流节点，必须只输出一个合法JSON对象。"},
            {"role": "user", "content": prompt},
        ],
        temperature=0.1,
        timeout=45,
    )


def _normalize_plan(plan: SummaryPlan) -> SummaryPlan:
    expected = {"simple": 1, "medium": 2, "complex": 3}[plan.complexity]
    return plan.model_copy(update={
        "worker_count": expected,
        "needs_deep": plan.needs_deep or plan.confidence < 0.6,
        "needs_search": bool(plan.needs_search and plan.search_query.strip()),
        "unknown_terms": [str(item).strip()[:40] for item in plan.unknown_terms if str(item).strip()][:5],
        "target_chars": max(40, min(260, plan.target_chars)),
    })


def _worker_tasks(plan: SummaryPlan) -> list[dict[str, Any]]:
    return [
        {"candidate_index": index, "strategy": _STRATEGIES[index]}
        for index in range(plan.worker_count)
    ]


def _fallback_target_chars(content: str) -> int:
    length = len(content)
    if length <= 300:
        return 70
    if length <= 1200:
        return 120
    return 180


def _normalize_summary(text: str) -> str:
    value = str(text or "").strip()
    value = re.sub(r"^```(?:text|markdown)?\s*|\s*```$", "", value, flags=re.IGNORECASE)
    value = re.sub(r"^(?:总结|摘要|内容概括)[:：]\s*", "", value)
    return value.strip()[:520]


def _valid_summary(text: str) -> bool:
    value = _normalize_summary(text)
    return bool(value and len(value) >= 10 and len(_AI_PHRASES.findall(value)) <= 2)


def _best_candidate(candidates: list[dict[str, Any]], target_chars: int) -> dict[str, Any]:
    return min(
        candidates,
        key=lambda item: abs(len(item.get("content", "")) - target_chars)
        + len(_AI_PHRASES.findall(item.get("content", ""))) * 20,
    )


def _flash_model() -> str:
    return flash_model_name()


def _deep_model() -> str:
    return deep_model_name()


_builder = StateGraph(SummaryState)
_builder.add_node("analyze", node_analyze)
_builder.add_node("search", node_search)
_builder.add_node("summary_worker", node_summary_worker)
_builder.add_node("evaluate", node_evaluate)
_builder.add_node("refine", node_refine)
_builder.add_edge(START, "analyze")
_builder.add_edge("analyze", "search")
_builder.add_conditional_edges("search", assign_workers, ["summary_worker"])
_builder.add_edge("summary_worker", "evaluate")
_builder.add_conditional_edges("evaluate", route_after_evaluate, {"refine": "refine", "done": END})
_builder.add_edge("refine", END)
_SUMMARY_GRAPH = _builder.compile()
