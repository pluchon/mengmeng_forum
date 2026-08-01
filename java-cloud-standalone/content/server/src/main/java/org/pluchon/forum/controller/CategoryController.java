package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.board.CategoryWithBoards;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.board.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分类模块", description = "获取分类及层级版块菜单接口")
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "获取分类及对应的版块列表", description = "用于展示侧边栏或顶部的层级导航菜单")
    @GetMapping("/getCategoryWithBoards")
    public Result<List<CategoryWithBoards>> getCategoryWithBoards() {
        return Result.success(categoryService.getCategoryWithBoards());
    }

    @Operation(summary = "获取指定分类下的所有帖子(分页)", description = "一次查出该分类下所有板块的帖子，避免前端逐板块请求")
    @GetMapping("/articles")
    public Result<PageResult<ArticleListResponse>> getArticlesByCategoryWithPage(Long categoryId,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(categoryService.getArticlesByCategoryWithPage(categoryId, pageNum, pageSize));
    }
}
