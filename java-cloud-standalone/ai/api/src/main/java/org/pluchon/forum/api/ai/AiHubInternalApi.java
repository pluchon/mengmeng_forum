package org.pluchon.forum.api.ai;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// AI 域内部契约 纯 API，无 @FeignClient
public interface AiHubInternalApi {

    @PostMapping("/ai/internal/hub/polish")
    AiHubPolishResultVO polish(@RequestParam("userId") Long userId, @Valid @RequestBody AiPolishRequest request);

    @PostMapping("/ai/internal/hub/article-cover")
    AiHubArticleCoverResultVO articleCover(@RequestParam("userId") Long userId,
                                           @Valid @RequestBody AiArticleCoverRequest request);

    @PostMapping("/ai/internal/hub/article-tag/recommend")
    AiHubArticleTagRecommendResultVO recommendArticleTags(
            @Valid @RequestBody AiArticleTagRecommendRequest request);

    @PostMapping("/ai/internal/hub/article-tag/similarity")
    AiHubArticleTagSimilarityResultVO checkArticleTagSimilarity(
            @Valid @RequestBody AiArticleTagSimilarityRequest request);

    @PostMapping("/ai/internal/hub/music/recommend")
    AiHubMusicMatchResultVO recommendMusic(@Valid @RequestBody AiMusicRecommendRequest request);

    @PostMapping("/ai/internal/hub/music/search")
    AiHubMusicMatchResultVO searchMusic(@Valid @RequestBody AiMusicSearchRequest request);

    @PostMapping("/ai/internal/hub/cover-hints")
    AiHubCoverHintsResultVO coverHints(@RequestParam("userId") Long userId,
                                       @Valid @RequestBody AiCoverHintsRequest request);

    @PostMapping("/ai/internal/hub/image")
    AiHubImageResultVO image(@RequestParam("userId") Long userId, @Valid @RequestBody AiImageRequest request);

    @PostMapping("/ai/internal/hub/recommendation/article-feature")
    AiRecommendationFeatureResultVO generateRecommendationArticleFeature(
            @Valid @RequestBody AiRecommendationArticleFeatureRequest request);

    @PostMapping("/ai/internal/hub/recommendation/profile")
    AiRecommendationProfileResultVO generateRecommendationProfile(
            @RequestParam("userId") Long userId,
            @Valid @RequestBody AiRecommendationProfileRequest request);

    @PostMapping("/ai/internal/hub/recommendation/music-taste")
    AiHubMusicMatchResultVO recommendMusicTaste(
            @RequestParam("userId") Long userId,
            @Valid @RequestBody AiMusicTasteRecommendRequest request);

    @PostMapping("/ai/internal/hub/summary")
    String summarize(@RequestParam("content") String content);

    @PostMapping("/ai/internal/hub/creator-insight")
    AiHubCreatorInsightResultVO generateCreatorInsight(
            @Valid @RequestBody AiCreatorInsightRequest request);

    @PostMapping("/ai/internal/hub/rag/article-index")
    void indexArticleRag(@Valid @RequestBody RagArticleIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/emoji-index")
    void indexEmojiRag(@Valid @RequestBody RagEmojiIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/music-index")
    void indexMusicRag(@Valid @RequestBody RagMusicIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/user-index")
    void indexUserRag(@Valid @RequestBody RagUserIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/article-remove/{articleId}")
    void removeArticleRag(@PathVariable("articleId") Long articleId);

    @PostMapping("/ai/internal/hub/rag/article-search")
    List<Long> ragVectorSearchArticles(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/rag/emoji-search")
    List<Long> ragVectorSearchEmojis(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/rag/music-search")
    List<String> ragVectorSearchMusic(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/rag/article-ranked")
    List<RagArticleVectorHitVO> ragArticleVectorRanked(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/rag/user-search")
    List<Long> ragVectorSearchUsers(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/rag/user-ranked")
    List<RagUserVectorHitVO> ragUserVectorRanked(@RequestParam("query") String query);

    @PostMapping("/ai/internal/hub/rag/candidate-ranked")
    List<Long> rankSemanticCandidates(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/moderation/text")
    String validateText(@RequestParam("content") String content);

    /** 图片 AI 审核（按 OSS URL；上传链路传 objectKey 供 Python SDK 直读） */
    @PostMapping("/ai/internal/hub/moderation/image-url")
    Boolean validateImageUrl(@Valid @RequestBody AiImageModerationUrlRequest request);

    /** 批量图片 AI 审核（按 OSS URL + objectKey） */
    @PostMapping("/ai/internal/hub/moderation/image-urls")
    List<AiImageModerationItemResultVO> validateImageUrls(@Valid @RequestBody AiImageModerationBatchUrlRequest request);

    /** @deprecated 上传审图请用 image-url；保留兼容旧调用 */
    @PostMapping("/ai/internal/hub/moderation/image-payload")
    Boolean validateImagePayload(@Valid @RequestBody AiImageModerationRequest request);

    @PostMapping("/ai/internal/hub/game/gobang-move")
    AiGobangMoveVO chooseGobangMove(@Valid @RequestBody AiGobangMoveRequest request);
}
