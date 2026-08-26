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
import org.pluchon.forum.entity.dto.article.ArticleTagSuggestRequest;
import org.pluchon.forum.entity.vo.article.ArticleTagFeedbackVO;
import org.pluchon.forum.entity.vo.article.ArticleTagVO;
import org.pluchon.forum.entity.vo.common.PageResult;
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

    /** 分页查询版块可选标签 */
    @Operation(summary = "分页查询版块可选标签")
    @GetMapping("/list")
    public Result<PageResult<ArticleTagVO>> list(
            @RequestParam Long boardId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            HttpServletRequest request) {
        if (keyword != null && !keyword.isBlank()) {
            requireLogin(request);
        }
        return Result.success(articleTagService.pageForBoard(boardId, keyword, pageNum));
    }

    /** 根据完整帖子内容推荐已有标签 */
    @Operation(summary = "AI 推荐已有标签")
    @PostMapping("/suggest")
    public Result<List<ArticleTagVO>> suggest(
            @RequestBody ArticleTagSuggestRequest body,
            HttpServletRequest request) {
        AuthenticatedUser user = requireLogin(request);
        Long boardId = body == null ? null : body.getBoardId();
        String title = body == null ? null : body.getTitle();
        String content = body == null ? null : body.getContent();
        String editorMode = body == null ? null : body.getEditorMode();
        return Result.success(articleTagService.suggestTags(user.getId(), boardId, title, content, editorMode));
    }

    @Operation(summary = "申请新标签（AI 审核通过后入库并站内信通知）")
    @PostMapping("/feedback")
    public Result<ArticleTagFeedbackVO> feedback(
            @RequestBody ArticleTagFeedbackRequest body,
            HttpServletRequest request) {
        AuthenticatedUser user = requireLogin(request);
        Long boardId = body == null ? null : body.getBoardId();
        String name = body == null || body.getProposedName() == null ? "" : body.getProposedName();
        String colorKey = body == null ? null : body.getColorKey();
        return Result.success(articleTagService.submitTagFeedback(user.getId(), boardId, name, colorKey));
    }

    private AuthenticatedUser requireLogin(HttpServletRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user;
    }
}
