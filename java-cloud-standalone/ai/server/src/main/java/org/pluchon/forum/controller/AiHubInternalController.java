package org.pluchon.forum.controller;

import jakarta.validation.Valid;
import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;
import org.pluchon.forum.api.ai.AiRagSearchRequest;
import org.pluchon.forum.entity.dto.*;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubCreatorInsightResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleCoverResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagRecommendResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagSimilarityResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageModerationItemResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// AI 内部 HTTP 接口
@RestController
public class AiHubInternalController implements AiHubInternalApi {

    @Autowired
    private AiHubService aiHubService;

    @Override
    public AiHubPolishResultVO polish(@RequestParam("userId") Long userId,
                                      @Valid @RequestBody AiPolishRequest request) {
        return aiHubService.polish(userId, request);
    }

    // 执行帖子封面生成子图
    @Override
    public AiHubArticleCoverResultVO articleCover(@RequestParam("userId") Long userId,
                                                  @Valid @RequestBody AiArticleCoverRequest request) {
        return aiHubService.articleCover(userId, request);
    }

    // 推荐帖子已有标签
    @Override
    public AiHubArticleTagRecommendResultVO recommendArticleTags(
            @Valid @RequestBody AiArticleTagRecommendRequest request) {
        return aiHubService.recommendArticleTags(request);
    }

    // 严格确认新标签是否与已有标签高度相似
    @Override
    public AiHubArticleTagSimilarityResultVO checkArticleTagSimilarity(
            @Valid @RequestBody AiArticleTagSimilarityRequest request) {
        return aiHubService.checkArticleTagSimilarity(request);
    }

    @Override
    public AiHubMusicMatchResultVO recommendMusic(@Valid @RequestBody AiMusicRecommendRequest request) {
        return aiHubService.recommendMusic(request);
    }

    @Override
    public AiHubMusicMatchResultVO searchMusic(@Valid @RequestBody AiMusicSearchRequest request) {
        return aiHubService.searchMusic(request);
    }

    @Override
    public AiHubCoverHintsResultVO coverHints(@RequestParam("userId") Long userId,
                                              @Valid @RequestBody AiCoverHintsRequest request) {
        return aiHubService.coverHints(userId, request);
    }

    @Override
    public AiHubImageResultVO image(@RequestParam("userId") Long userId,
                                    @Valid @RequestBody AiImageRequest request) {
        return aiHubService.image(userId, request);
    }

    @Override
    public AiRecommendationFeatureResultVO generateRecommendationArticleFeature(
            @Valid @RequestBody AiRecommendationArticleFeatureRequest request) {
        return aiHubService.generateRecommendationArticleFeature(request);
    }

    @Override
    public AiRecommendationProfileResultVO generateRecommendationProfile(
            @RequestParam("userId") Long userId,
            @Valid @RequestBody AiRecommendationProfileRequest request) {
        return aiHubService.generateRecommendationProfile(userId, request);
    }

    @Override
    public AiHubMusicMatchResultVO recommendMusicTaste(
            @RequestParam("userId") Long userId,
            @Valid @RequestBody AiMusicTasteRecommendRequest request) {
        return aiHubService.recommendMusicTaste(userId, request);
    }

    @Override
    public String summarize(@RequestParam("content") String content) {
        return aiHubService.summarize(content);
    }

    // 生成创作中心数据小结
    @Override
    public AiHubCreatorInsightResultVO generateCreatorInsight(
            @Valid @RequestBody AiCreatorInsightRequest request) {
        return aiHubService.generateCreatorInsight(request);
    }

    @Override
    public void indexArticleRag(@Valid @RequestBody RagArticleIndexDTO payload) {
        aiHubService.indexArticleRag(payload);
    }

    @Override
    public void indexEmojiRag(@Valid @RequestBody RagEmojiIndexDTO payload) {
        aiHubService.indexEmojiRag(payload);
    }

    @Override
    public void indexMusicRag(@Valid @RequestBody RagMusicIndexDTO payload) {
        aiHubService.indexMusicRag(payload);
    }

    @Override
    public void indexUserRag(@Valid @RequestBody RagUserIndexDTO payload) {
        aiHubService.indexUserRag(payload);
    }

    @Override
    public void removeArticleRag(@PathVariable("articleId") Long articleId) {
        aiHubService.removeArticleRag(articleId);
    }

    @Override
    public List<Long> ragVectorSearchArticles(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.ragVectorSearchArticles(request.getQuery(), request.getCandidates());
    }

    @Override
    public List<Long> ragVectorSearchEmojis(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.ragVectorSearchEmojis(request.getQuery());
    }

    @Override
    public List<String> ragVectorSearchMusic(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.ragVectorSearchMusic(request.getQuery());
    }

    @Override
    public List<RagArticleVectorHitVO> ragArticleVectorRanked(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.ragArticleVectorRanked(request.getQuery(), request.getCandidates());
    }

    @Override
    public List<RagUserVectorHitVO> ragUserVectorRanked(@RequestParam("query") String query) {
        return aiHubService.ragUserVectorRanked(query);
    }

    @Override
    public List<Long> rankSemanticCandidates(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.rankSemanticCandidates(request.getQuery(), request.getCandidates());
    }

    @Override
    public String validateText(@RequestParam("content") String content) {
        return aiHubService.validateText(content);
    }

    @Override
    public Boolean validateImageUrl(@Valid @RequestBody AiImageModerationUrlRequest request) {
        return aiHubService.validateImageUrl(
                request.getImageUrl(),
                request.getObjectKey());
    }

    @Override
    public List<AiImageModerationItemResultVO> validateImageUrls(
            @Valid @RequestBody AiImageModerationBatchUrlRequest request) {
        return aiHubService.validateImageUrls(request);
    }

    @Override
    public AiGobangMoveVO chooseGobangMove(@Valid @RequestBody AiGobangMoveRequest request) {
        return aiHubService.chooseGobangMove(request);
    }
}
