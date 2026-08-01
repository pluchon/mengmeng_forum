package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.ReplyArticleRequest;
import org.example.forumdemo.entity.vo.article.ArticleReplyListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.article.ArticleReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "帖子回复模块", description = "帖子回复的增删改查接口")
@RestController
@RequestMapping("/articleReply")
public class ArticleReplyController {

    @Autowired
    private ArticleReplyService articleReplyService;

    @Operation(summary = "回复帖子", description = "传入回复信息，包括帖子ID、内容")
    @PutMapping("/replyArticle")
    public Result<String> replyArticle(@RequestBody ReplyArticleRequest replyArticleRequest, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        articleReplyService.replyArticle(replyArticleRequest, loginUser.getId());
        return Result.success("回复成功");
    }

    @Operation(summary = "帖子回复列表(分页)", description = "传入帖子ID和分页参数")
    @GetMapping("/getArticleReplyByArticleIdWithPage")
    public Result<PageResult<ArticleReplyListResponse>> getArticleReplyByArticleIdWithPage(Long articleId,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser != null ? loginUser.getId() : null;
        return Result.success(articleReplyService.queryReplyByArticleIdWithPage(
                articleId, pageNum, pageSize, loginUserId));
    }

    @Operation(summary = "删除帖子回复", description = "传入回复ID，只有回复作者或帖子楼主可以删除")
    @DeleteMapping("/deleteReply")
    public Result<String> deleteReply(Long replyId, HttpServletRequest httpServletRequest) {
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        articleReplyService.deleteReply(replyId, loginUser.getId());
        return Result.success("删除成功");
    }
}
