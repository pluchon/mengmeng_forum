package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.ArticleListByLikeResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.article.ArticleLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "帖子点赞模块", description = "帖子点赞 / 取消点赞 / 谁点赞了我的帖子")
@RestController
@RequestMapping("/like")
public class ArticleLikeController {

    @Autowired
    private ArticleLikeService articleLikeService;

    @Operation(summary = "点赞帖子", description = "传入帖子ID进行点赞")
    @PutMapping("/likeArticle")
    public Result<String> likeArticle(Long articleId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleLikeService.likeArticle(articleId, loginUser.getId());
        return Result.success("点赞成功");
    }

    @Operation(summary = "取消点赞", description = "传入帖子ID取消点赞")
    @PutMapping("/unlikeArticle")
    public Result<String> unlikeArticle(Long articleId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
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
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return Result.success(articleLikeService.queryArticleListForLikeWithPage(loginUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "查看谁点赞了我的帖子", description = "仅帖子作者本人可调用")
    @GetMapping("/queryWhoLikedArticle")
    public Result<List<User>> queryWhoLikedArticle(Long articleId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long userId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleLikeService.queryWhoLikedArticle(articleId, userId));
    }

    @Operation(summary = "查看最新点赞的用户信息", description = "仅帖子作者本人可调用，按时间倒序返回最新点赞的 N 位用户")
    @GetMapping("/getLatestLikerUsers")
    public Result<List<User>> getLatestLikerUsers(Long articleId, @RequestParam(defaultValue = "15") Integer count, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long userId = (loginUser != null) ? loginUser.getId() : -1L;
        return Result.success(articleLikeService.getLatestLikerUsers(articleId, userId, count));
    }
}
