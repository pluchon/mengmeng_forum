package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.ArticleTagFeedbackVO;
import org.pluchon.forum.entity.vo.article.ArticleTagVO;
import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

public interface ArticleTagService {

    PageResult<ArticleTagVO> pageForBoard(Long boardId, String keyword, Integer pageNum);

    List<ArticleTagVO> listForBoard(Long boardId);

    void bindArticleTags(Long articleId, Long boardId, List<Long> tagIds);

    List<ArticleTagVO> listByArticleId(Long articleId);

    List<String> tagNamesByArticleId(Long articleId);

    List<ArticleTagVO> suggestTags(Long userId, Long boardId, String title, String content, String editorMode);

    ArticleTagFeedbackVO submitTagFeedback(Long userId, Long boardId, String proposedName, String colorKey);
}
