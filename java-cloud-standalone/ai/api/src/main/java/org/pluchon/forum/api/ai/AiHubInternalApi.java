package org.pluchon.forum.api.ai;

import jakarta.validation.Valid;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// AI 域内部契约（纯 API，无 @FeignClient）
public interface AiHubInternalApi {

    @PostMapping("/ai/internal/hub/polish")
    AiHubPolishResultVO polish(@RequestParam("userId") Long userId, @Valid @RequestBody AiPolishRequest request);

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

    @PostMapping("/ai/internal/hub/summary")
    String summarize(@RequestParam("content") String content);

    @PostMapping("/ai/internal/hub/rag/article-index")
    void indexArticleRag(@Valid @RequestBody RagArticleIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/emoji-index")
    void indexEmojiRag(@Valid @RequestBody RagEmojiIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/user-index")
    void indexUserRag(@Valid @RequestBody RagUserIndexDTO payload);

    @PostMapping("/ai/internal/hub/rag/article-remove/{articleId}")
    void removeArticleRag(@PathVariable("articleId") Long articleId);

    @PostMapping("/ai/internal/hub/rag/article-search")
    List<Long> ragVectorSearchArticles(@Valid @RequestBody AiRagSearchRequest request);

    @PostMapping("/ai/internal/hub/rag/emoji-search")
    List<Long> ragVectorSearchEmojis(@Valid @RequestBody AiRagSearchRequest request);

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

    @PostMapping(value = "/ai/internal/hub/moderation/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Boolean validateImage(@RequestPart("file") MultipartFile file);

    @PostMapping("/ai/internal/hub/game/gobang-move")
    AiGobangMoveVO chooseGobangMove(@Valid @RequestBody AiGobangMoveRequest request);
}
