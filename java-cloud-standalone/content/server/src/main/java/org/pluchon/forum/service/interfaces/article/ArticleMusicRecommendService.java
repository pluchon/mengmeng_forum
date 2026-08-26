package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.dto.article.MusicAiSearchRequest;
import org.pluchon.forum.entity.dto.article.MusicRecommendRequest;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;

// 帖子配乐 AI 推荐
public interface ArticleMusicRecommendService {

    MusicMatchResultVO recommend(Long userId, MusicRecommendRequest request);
}
