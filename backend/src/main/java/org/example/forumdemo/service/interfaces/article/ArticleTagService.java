package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.vo.article.ArticleTagVO;

import java.util.List;

public interface ArticleTagService {

    List<ArticleTagVO> listForBoard(Long boardId);

    void bindArticleTags(Long articleId, Long boardId, List<Long> tagIds);

    List<ArticleTagVO> listByArticleId(Long articleId);

    List<String> tagNamesByArticleId(Long articleId);

    List<ArticleTagVO> suggestTags(Long boardId, String title, String contentSnippet);

    Long submitTagFeedback(Long userId, Long boardId, String proposedName);
}
