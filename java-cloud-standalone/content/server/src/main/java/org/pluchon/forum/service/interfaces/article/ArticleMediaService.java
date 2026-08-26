package org.pluchon.forum.service.interfaces.article;

import java.util.Collection;
import java.util.List;
import java.util.Map;

// 帖子封面、相册与视频媒体落库
public interface ArticleMediaService {

    void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId);

    void replaceArticleImages(Long articleId, Long loginUserId, List<String> imageUrls);

    void setArticleVideo(Long articleId, Long loginUserId, String videoUrl);

    void clearArticleVideo(Long articleId, Long loginUserId);

    // 绑定帖子配乐（与相册/视频正交）
    void setArticleMusic(Long articleId, Long loginUserId,
                         String musicKey, String musicTitle,
                         String musicCoverUrl, String musicAudioUrl, String musicLrcUrl);

    void clearArticleMusic(Long articleId, Long loginUserId);

    List<String> queryArticleImageUrls(Long articleId);

    // 批量统计帖子相册图片数量 未删除
    Map<Long, Integer> countImagesByArticleIds(Collection<Long> articleIds);

    // 批量取相册第一张图 URL（按 sort、id 升序）
    Map<Long, String> firstImageUrlByArticleIds(Collection<Long> articleIds);
}
