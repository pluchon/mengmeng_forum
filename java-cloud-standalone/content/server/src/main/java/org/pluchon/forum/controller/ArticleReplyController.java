package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.ReplyArticleRequest;
import org.pluchon.forum.entity.vo.article.ArticleReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.article.ArticleReplyService;
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
    public Result<ArticleReplyListResponse> replyArticle(@Valid @RequestBody ReplyArticleRequest replyArticleRequest,
                                                        HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(articleReplyService.replyArticle(replyArticleRequest, loginUser.getId()));
    }

    /** 删除自己发的楼层，楼中楼保留 */
    @Operation(summary = "删除自己的回复", description = "只能删自己发的，楼中楼保留")
    @DeleteMapping("/deleteOwnReply")
    public Result<Void> deleteOwnReply(@RequestParam Long replyId, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        articleReplyService.deleteOwnReply(replyId, loginUser.getId());
        return Result.success();
    }

    @Operation(summary = "帖子回复列表(分页)", description = "传入帖子ID和分页参数")
    @GetMapping("/getArticleReplyByArticleIdWithPage")
    public Result<PageResult<ArticleReplyListResponse>> getArticleReplyByArticleIdWithPage(Long articleId,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser != null ? loginUser.getId() : null;
        return Result.success(articleReplyService.queryReplyByArticleIdWithPage(
                articleId, pageNum, pageSize, loginUserId));
    }
}
