package org.pluchon.forum.controller;

import jakarta.validation.Valid;
import org.pluchon.forum.api.ai.AiHubInternalApi;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;
import org.pluchon.forum.api.ai.AiRagSearchRequest;
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
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public String summarize(@RequestParam("content") String content) {
        return aiHubService.summarize(content);
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
    public List<RagArticleVectorHitVO> ragArticleVectorRanked(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.ragArticleVectorRanked(request.getQuery(), request.getCandidates());
    }

    @Override
    public List<Long> ragVectorSearchUsers(@Valid @RequestBody AiRagSearchRequest request) {
        return aiHubService.ragVectorSearchUsers(request.getQuery(), request.getCandidates());
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
    public Boolean validateImage(@RequestPart("file") MultipartFile file) {
        return aiHubService.validateImage(file);
    }

    @Override
    public AiGobangMoveVO chooseGobangMove(@Valid @RequestBody AiGobangMoveRequest request) {
        return aiHubService.chooseGobangMove(request);
    }
}
