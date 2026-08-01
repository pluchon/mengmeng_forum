package org.example.forumdemo.service.interfaces.article;

import java.util.List;

// 帖子封面、相册与视频媒体落库
public interface ArticleMediaService {

    void updateArticleCoverByUrl(Long articleId, String coverUrl, Long loginUserId);

    void replaceArticleImages(Long articleId, Long loginUserId, List<String> imageUrls);

    void setArticleVideo(Long articleId, Long loginUserId, String videoUrl);

    void clearArticleVideo(Long articleId, Long loginUserId);

    List<String> queryArticleImageUrls(Long articleId);
}
