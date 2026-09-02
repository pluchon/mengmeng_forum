# -*- coding: utf-8 -*-
"""升级率观测。

站内有两处「让模型自报置信度、据此决定要不要换更贵的模型」：
  - 文本审核 modules/moderation/graph.py  borderline or confidence < 0.72
  - 正文润色 modules/creation/polish_graph.py  needs_deep or confidence < 0.65
LLM 的自报置信度校准很差，这两个阈值定得对不对，光看代码看不出来——
而文本审核是全站 QPS 最高的 AI 路径（帖子标题、昵称、简介、标签、收藏夹名都走它），
升级率差十个百分点，账单就差一大截。

所以先埋观测再谈调参：进程内数着，每满一批打一行日志。
不引 Prometheus，也不落库——这是拿来定阈值的临时尺子，不是长期指标。
"""
from __future__ import annotations

import logging
import threading

logger = logging.getLogger(__name__)

# 每满这么多次打一行汇总
_BATCH = 50

_lock = threading.Lock()
_counters: dict[str, list[int]] = {}


def record(scene: str, escalated: bool) -> None:
    """记一次判定。scene 用来区分场景，如 text_audit / polish。"""
    with _lock:
        slot = _counters.setdefault(scene, [0, 0, 0, 0])  # 本批总数, 本批升级, 累计总数, 累计升级
        slot[0] += 1
        slot[2] += 1
        if escalated:
            slot[1] += 1
            slot[3] += 1
        if slot[0] < _BATCH:
            return
        batch_total, batch_deep, all_total, all_deep = slot
        slot[0] = 0
        slot[1] = 0
    logger.info(
        "[升级率] %s 最近%d次 升级%d (%.1f%%) | 累计%d次 升级%d (%.1f%%)",
        scene, batch_total, batch_deep, batch_deep * 100.0 / batch_total,
        all_total, all_deep, all_deep * 100.0 / all_total,
    )


def snapshot() -> dict[str, dict[str, int]]:
    """当前累计值，健康检查/排查时可读。"""
    with _lock:
        return {k: {"total": v[2], "escalated": v[3]} for k, v in _counters.items()}
