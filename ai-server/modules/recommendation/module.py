"""统一 Gateway 可调用的推荐模块。"""

from __future__ import annotations

import asyncio

from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult

from .music_taste_graph import run_music_taste_graph
from .service import RecommendationFeatureService
from .user_profile_graph import run_user_profile_graph

_service = RecommendationFeatureService()


class ArticleFeatureModule:
    """生成公开帖子可解释的推荐特征。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        try:
            data = await _service.article_feature(request.payload, request.trace_id)
        except ValueError as exc:
            raise ModuleRequestError("INVALID_RECOMMENDATION_PAYLOAD", str(exc)) from exc
        return ModuleResult(success=True, data=data)


class UserProfileModule:
    """根据脱敏聚合信号更新用户推荐画像（正/负并行 + preferenceQuery）。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        data = await asyncio.to_thread(
            run_user_profile_graph,
            request.payload or {},
            request.trace_id or "",
        )
        return ModuleResult(success=True, data=data)


class MusicTasteModule:
    """音乐大厅个人品味片单：并行子代理 + 候选挑选。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        favorites = request.payload.get("favorites")
        recent_plays = request.payload.get("recentPlays")
        extras = request.payload.get("extras")
        candidates = request.payload.get("candidates")
        if not isinstance(candidates, list) or not candidates:
            raise ModuleRequestError("INVALID_MUSIC_TASTE_PAYLOAD", "candidates 必须为非空数组")
        if favorites is not None and not isinstance(favorites, list):
            raise ModuleRequestError("INVALID_MUSIC_TASTE_PAYLOAD", "favorites 必须为数组")
        if recent_plays is not None and not isinstance(recent_plays, list):
            raise ModuleRequestError("INVALID_MUSIC_TASTE_PAYLOAD", "recentPlays 必须为数组")
        if extras is not None and not isinstance(extras, list):
            raise ModuleRequestError("INVALID_MUSIC_TASTE_PAYLOAD", "extras 必须为数组")
        result = await asyncio.to_thread(
            run_music_taste_graph,
            favorites or [],
            recent_plays or [],
            extras or [],
            candidates[:200],
        )
        return ModuleResult(
            success=True,
            data={
                "musicKeys": result.get("musicKeys") or [],
                "rationale": result.get("rationale") or "",
                "artifactType": "MUSIC_TASTE_SLATE",
            },
            usage=result.get("usage") or {},
        )
