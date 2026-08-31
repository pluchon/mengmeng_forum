package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicHotTrackVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

// 音乐大厅发现页
public interface ArticleMusicDiscoverService {

    MusicTrackVO getFeatured();

    /**
     * 发现页推荐。
     *
     * @param moods 用户勾选的氛围标签，命中任一即召回（AND 语义会因为标签是 AI 打的而经常空集）
     */
    PageResult<MusicTrackVO> pageRecommend(Long userId, List<String> moods, Integer pageNum, Integer pageSize);

    PageResult<MusicHotTrackVO> pageHot(Integer pageNum, Integer pageSize);
}
