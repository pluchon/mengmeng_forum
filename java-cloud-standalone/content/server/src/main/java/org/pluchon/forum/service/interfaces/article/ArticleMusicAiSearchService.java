package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.dto.article.MusicAiSearchRequest;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;

// 曲库 AI 搜索
public interface ArticleMusicAiSearchService {

    MusicMatchResultVO search(Long userId, MusicAiSearchRequest request);
}
