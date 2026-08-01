package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.ArticleTagFeedbackVO;
import org.pluchon.forum.entity.vo.article.ArticleTagVO;

import java.util.List;

public interface ArticleTagService {

    List<ArticleTagVO> listForBoard(Long boardId);

    void bindArticleTags(Long articleId, Long boardId, List<Long> tagIds);

    List<ArticleTagVO> listByArticleId(Long articleId);

    List<String> tagNamesByArticleId(Long articleId);

    List<ArticleTagVO> suggestTags(Long boardId, String title, String contentSnippet);

    ArticleTagFeedbackVO submitTagFeedback(Long userId, Long boardId, String proposedName);
}
