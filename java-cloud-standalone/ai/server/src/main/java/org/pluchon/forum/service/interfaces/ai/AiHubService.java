package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.entity.dto.AiArticleCoverRequest;
import org.pluchon.forum.entity.dto.AiArticleTagRecommendRequest;
import org.pluchon.forum.entity.dto.AiArticleTagSimilarityRequest;
import org.pluchon.forum.entity.dto.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.AiCreatorInsightRequest;
import org.pluchon.forum.entity.dto.AiImageModerationBatchUrlRequest;
import org.pluchon.forum.entity.dto.AiImageRequest;
import org.pluchon.forum.entity.dto.AiMusicRecommendRequest;
import org.pluchon.forum.entity.dto.AiMusicSearchRequest;
import org.pluchon.forum.entity.dto.AiMusicTasteRecommendRequest;
import org.pluchon.forum.entity.dto.AiPolishRequest;
import org.pluchon.forum.entity.dto.AiRecommendationArticleFeatureRequest;
import org.pluchon.forum.entity.dto.AiRecommendationProfileRequest;
import org.pluchon.forum.entity.dto.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.RagEmojiIndexDTO;
import org.pluchon.forum.entity.dto.RagMusicIndexDTO;
import org.pluchon.forum.entity.dto.RagUserIndexDTO;
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
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;

import java.util.List;
import java.util.Map;

public interface AiHubService {

    AiHubPolishResultVO polish(Long userId, AiPolishRequest request);

    AiHubArticleCoverResultVO articleCover(Long userId, AiArticleCoverRequest request);

    AiHubArticleTagRecommendResultVO recommendArticleTags(AiArticleTagRecommendRequest request);

    AiHubArticleTagSimilarityResultVO checkArticleTagSimilarity(AiArticleTagSimilarityRequest request);

    AiHubMusicMatchResultVO recommendMusic(AiMusicRecommendRequest request);

    AiHubMusicMatchResultVO searchMusic(AiMusicSearchRequest request);

    AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest request);

    AiHubImageResultVO image(Long userId, AiImageRequest request);

    AiRecommendationFeatureResultVO generateRecommendationArticleFeature(AiRecommendationArticleFeatureRequest request);

    AiRecommendationProfileResultVO generateRecommendationProfile(Long userId, AiRecommendationProfileRequest request);

    AiHubMusicMatchResultVO recommendMusicTaste(Long userId, AiMusicTasteRecommendRequest request);

    String summarize(String content);

    AiHubCreatorInsightResultVO generateCreatorInsight(AiCreatorInsightRequest request);

    void indexArticleRag(RagArticleIndexDTO payload);

    void indexEmojiRag(RagEmojiIndexDTO payload);

    void indexMusicRag(RagMusicIndexDTO payload);

    void indexUserRag(RagUserIndexDTO payload);

    void removeArticleRag(Long articleId);

    List<Long> ragVectorSearchArticles(String query, List<Map<String, Object>> candidates);

    List<Long> ragVectorSearchEmojis(String query);

    List<String> ragVectorSearchMusic(String query);

    List<RagArticleVectorHitVO> ragArticleVectorRanked(String query, List<Map<String, Object>> candidates);

    List<RagUserVectorHitVO> ragUserVectorRanked(String query);

    List<Long> rankSemanticCandidates(String query, List<Map<String, Object>> candidates);

    String validateText(String content);

    boolean validateImageUrl(String imageUrl, String objectKey);

    List<AiImageModerationItemResultVO> validateImageUrls(AiImageModerationBatchUrlRequest request);

    AiGobangMoveVO chooseGobangMove(AiGobangMoveRequest request);
}
