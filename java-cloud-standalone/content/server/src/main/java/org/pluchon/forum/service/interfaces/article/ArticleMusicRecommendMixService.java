package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicTrackVO;

import java.util.List;

// 日常非 AI 推荐混排：收藏 70% / 近播 15% / 其他 15%
public interface ArticleMusicRecommendMixService {

    List<MusicTrackVO> buildPersonalizedRecommend(Long userId, int limit);
}
