package org.pluchon.forum.service.interfaces.board;

import org.pluchon.forum.entity.db.Category;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.board.CategoryWithBoards;
import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

public interface CategoryService {

    // 获取所有分类，并带有其下的版块列表
    List<CategoryWithBoards> getCategoryWithBoards();

    // 获取指定分类下所有板块的帖子 带分页 ，解决 N+1 问题
    PageResult<ArticleListResponse> getArticlesByCategoryWithPage(Long categoryId, Integer pageNum, Integer pageSize);
}
