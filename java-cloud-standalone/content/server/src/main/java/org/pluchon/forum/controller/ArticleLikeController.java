package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.article.ArticleListByLikeResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.article.ArticleLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "帖子点赞模块", description = "帖子点赞 / 取消点赞 / 我的点赞列表")
@RestController
@RequestMapping("/like")
public class ArticleLikeController {

    @Autowired
    private ArticleLikeService articleLikeService;

    @Operation(summary = "点赞帖子", description = "传入帖子ID进行点赞")
    @PutMapping("/likeArticle")
    public Result<String> likeArticle(Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleLikeService.likeArticle(articleId, loginUser.getId());
        return Result.success("点赞成功");
    }

    @Operation(summary = "取消点赞", description = "传入帖子ID取消点赞")
    @PutMapping("/unlikeArticle")
    public Result<String> unlikeArticle(Long articleId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleLikeService.unlikeArticle(articleId, loginUser.getId());
        return Result.success("取消点赞成功");
    }

    @Operation(summary = "查询我的点赞列表(分页)", description = "获取当前登录用户点赞过的所有帖子")
    @GetMapping("/queryArticleListForLikeWithPage")
    public Result<PageResult<ArticleListByLikeResponse>> queryArticleListForLikeWithPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleLikeService.queryArticleListForLikeWithPage(loginUser.getId(), pageNum, pageSize));
    }

    /** 查询用户主页公开的点赞帖子 */
    @Operation(summary = "查询用户点赞列表", description = "返回指定用户点赞过且当前仍可公开访问的帖子")
    @GetMapping("/queryArticleListForUserLikeWithPage")
    public Result<PageResult<ArticleListByLikeResponse>> queryUserArticleListForLikeWithPage(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(articleLikeService.queryUserArticleListForLikeWithPage(userId, pageNum, pageSize));
    }

}
