package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.common.PageResult;

// 已发布且完成 AI 画像的用户曲库
public interface ArticleMusicCatalogService {

    PageResult<MusicTrackVO> pageCatalog(String keyword, String scope, String mood,
                                         Integer pageNum, Integer pageSize);
}
