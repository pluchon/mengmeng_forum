package org.example.forumdemo.service.interfaces.board;

import org.example.forumdemo.entity.db.Category;
import org.example.forumdemo.entity.vo.article.ArticleListResponse;
import org.example.forumdemo.entity.vo.board.CategoryWithBoards;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

public interface CategoryService {

    // 获取所有的分类
    List<Category> queryAllCategories();

    // 获取所有分类，并带有其下的版块列表
    List<CategoryWithBoards> getCategoryWithBoards();

    // 获取指定分类下所有板块的帖子（带分页），解决 N+1 问题
    PageResult<ArticleListResponse> getArticlesByCategoryWithPage(Long categoryId, Integer pageNum, Integer pageSize);
}
