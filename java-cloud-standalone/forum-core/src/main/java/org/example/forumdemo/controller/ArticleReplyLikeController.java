package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.service.interfaces.article.ArticleReplyLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "评论点赞模块", description = "一级评论与楼中楼点赞")
@RestController
@RequestMapping("/replyLike")
public class ArticleReplyLikeController {

    @Autowired
    private ArticleReplyLikeService articleReplyLikeService;

    @Operation(summary = "点赞一级评论")
    @PutMapping("/likeReply")
    public Result<String> likeReply(Long replyId, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleReplyLikeService.likeReply(replyId, loginUser.getId());
        return Result.success("点赞成功");
    }

    @Operation(summary = "取消点赞一级评论")
    @PutMapping("/unlikeReply")
    public Result<String> unlikeReply(Long replyId, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleReplyLikeService.unlikeReply(replyId, loginUser.getId());
        return Result.success("取消点赞成功");
    }

    @Operation(summary = "点赞楼中楼回复")
    @PutMapping("/likeSubReply")
    public Result<String> likeSubReply(Long subReplyId, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleReplyLikeService.likeSubReply(subReplyId, loginUser.getId());
        return Result.success("点赞成功");
    }

    @Operation(summary = "取消点赞楼中楼回复")
    @PutMapping("/unlikeSubReply")
    public Result<String> unlikeSubReply(Long subReplyId, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        articleReplyLikeService.unlikeSubReply(subReplyId, loginUser.getId());
        return Result.success("取消点赞成功");
    }
}
