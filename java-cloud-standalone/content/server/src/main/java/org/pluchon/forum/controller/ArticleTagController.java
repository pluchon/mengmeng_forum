package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.ArticleTagFeedbackRequest;
import org.pluchon.forum.entity.vo.article.ArticleTagFeedbackVO;
import org.pluchon.forum.entity.vo.article.ArticleTagVO;
import org.pluchon.forum.service.interfaces.article.ArticleTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "帖子标签")
@RestController
@RequestMapping("/article/tag")
public class ArticleTagController {

    @Autowired
    private ArticleTagService articleTagService;

    @Operation(summary = "按版块列出可选标签")
    @GetMapping("/list")
    public Result<List<ArticleTagVO>> list(@RequestParam Long boardId) {
        return Result.success(articleTagService.listForBoard(boardId));
    }

    @Operation(summary = "AI/规则推荐标签")
    @GetMapping("/suggest")
    public Result<List<ArticleTagVO>> suggest(
            @RequestParam Long boardId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content) {
        return Result.success(articleTagService.suggestTags(boardId, title, content));
    }

    @Operation(summary = "申请新标签（AI 审核通过后入库并站内信通知）")
    @PostMapping("/feedback")
    public Result<ArticleTagFeedbackVO> feedback(
            @RequestBody ArticleTagFeedbackRequest body,
            HttpServletRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        Long boardId = body == null ? null : body.getBoardId();
        String name = body == null || body.getProposedName() == null ? "" : body.getProposedName();
        return Result.success(articleTagService.submitTagFeedback(user.getId(), boardId, name));
    }
}
