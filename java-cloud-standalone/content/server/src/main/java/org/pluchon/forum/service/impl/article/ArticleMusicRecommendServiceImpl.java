package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.dto.AiMusicCandidateDTO;
import org.pluchon.forum.entity.dto.AiMusicRecommendRequest;
import org.pluchon.forum.entity.dto.article.MusicRecommendRequest;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
class ArticleMusicRecommendServiceImpl extends ArticleMusicAiSupport implements ArticleMusicRecommendService {

    @Override
    public MusicMatchResultVO recommend(Long userId, MusicRecommendRequest request) {
        String title = request == null ? "" : safeText(request.getTitle());
        String content = request == null ? "" : safeText(request.getContent());
        if (!StringUtils.hasText(title) && content.length() < 20) {
            return buildEmptyResult(EMPTY_RECOMMEND_HINT);
        }
        List<UserMusic> rows = articleUserMusicService.listPublishedWithAiProfile();
        // 推荐同样要防「盲取前 200」：曲库一大，靠后的歌永远不可能被推荐到。
        // 这里的相关度信号就是帖子本身，标题权重更高所以放前面。
        List<AiMusicCandidateDTO> candidates = buildCandidates(rows, title + " " + content);
        if (candidates.isEmpty()) {
            return buildEmptyResult(EMPTY_RECOMMEND_HINT);
        }
        AiMusicRecommendRequest aiRequest = new AiMusicRecommendRequest();
        aiRequest.setUserId(userId);
        aiRequest.setClientRequestId(request == null ? null : request.getClientRequestId());
        aiRequest.setTitle(title);
        aiRequest.setContent(content);
        aiRequest.setEditorMode(normalizeEditorMode(request == null ? null : request.getEditorMode()));
        aiRequest.setCandidates(candidates);
        AiHubMusicMatchResultVO aiResult = contentAiGatewayService.recommendMusic(aiRequest);
        return toResult(aiResult, rows, userId, EMPTY_RECOMMEND_HINT);
    }
}
