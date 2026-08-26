package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicHotTrackVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;

// 音乐大厅发现页
public interface ArticleMusicDiscoverService {

    MusicTrackVO getFeatured();

    PageResult<MusicTrackVO> pageRecommend(Long userId, Integer pageNum, Integer pageSize);

    PageResult<MusicHotTrackVO> pageHot(Integer pageNum, Integer pageSize);
}
