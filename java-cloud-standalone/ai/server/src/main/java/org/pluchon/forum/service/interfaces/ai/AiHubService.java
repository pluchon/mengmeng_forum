package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.entity.dto.ai.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.ai.AiImageRequest;
import org.pluchon.forum.entity.dto.ai.AiPolishRequest;
import org.pluchon.forum.entity.dto.ai.AiRecommendationArticleFeatureRequest;
import org.pluchon.forum.entity.dto.ai.AiRecommendationProfileRequest;
import org.pluchon.forum.entity.dto.ai.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.ai.RagEmojiIndexDTO;
import org.pluchon.forum.entity.dto.ai.RagUserIndexDTO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

public interface AiHubService {

    AiHubPolishResultVO polish(Long userId, AiPolishRequest request);

    AiHubCoverHintsResultVO coverHints(Long userId, AiCoverHintsRequest request);

    AiHubImageResultVO image(Long userId, AiImageRequest request);

    AiRecommendationFeatureResultVO generateRecommendationArticleFeature(AiRecommendationArticleFeatureRequest request);

    AiRecommendationProfileResultVO generateRecommendationProfile(Long userId, AiRecommendationProfileRequest request);

    String summarize(String content);

    void indexArticleRag(RagArticleIndexDTO payload);

    void indexEmojiRag(RagEmojiIndexDTO payload);

    void indexUserRag(RagUserIndexDTO payload);

    void removeArticleRag(Long articleId);

    List<Long> ragVectorSearchArticles(String query, List<Map<String, Object>> candidates);

    List<Long> ragVectorSearchEmojis(String query);

    List<RagArticleVectorHitVO> ragArticleVectorRanked(String query, List<Map<String, Object>> candidates);

    List<Long> ragVectorSearchUsers(String query, List<Map<String, Object>> candidates);

    List<RagUserVectorHitVO> ragUserVectorRanked(String query);

    List<Long> rankSemanticCandidates(String query, List<Map<String, Object>> candidates);

    String validateText(String content);

    boolean validateImage(MultipartFile file);

    AiGobangMoveVO chooseGobangMove(AiGobangMoveRequest request);
}
