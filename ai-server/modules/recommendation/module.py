"""统一 Gateway 可调用的推荐模块。"""

from __future__ import annotations

from runtime.contracts import ModuleRequest, ModuleRequestError, ModuleResult

from .service import RecommendationFeatureService

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
    """根据脱敏聚合信号更新用户推荐画像。"""

    async def run(self, request: ModuleRequest) -> ModuleResult:
        data = await _service.user_profile(request.payload, request.trace_id)
        return ModuleResult(success=True, data=data)
