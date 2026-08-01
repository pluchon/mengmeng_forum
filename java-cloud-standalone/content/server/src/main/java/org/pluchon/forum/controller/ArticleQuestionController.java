package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.AcceptQuestionAnswerRequest;
import org.pluchon.forum.entity.dto.article.CloseQuestionRequest;
import org.pluchon.forum.entity.vo.article.QuestionAnswerVO;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 问答帖状态与最佳答案接口
@Tag(name = "问答帖子", description = "问答状态与最佳答案")
@RestController
@RequestMapping("/articleQuestion")
public class ArticleQuestionController {

    // 问答帖业务
    @Autowired
    private ArticleQuestionService articleQuestionService;

    /** 采纳问答帖最佳答案。 */
    @PostMapping("/acceptAnswer")
    public Result<String> acceptAnswer(
            @Valid @RequestBody AcceptQuestionAnswerRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        articleQuestionService.acceptAnswer(request.getArticleId(), request.getReplyId(), loginUser.getId());
        return Result.success("已采纳最佳答案");
    }

    /** 关闭尚未解决的问答帖。 */
    @PostMapping("/close")
    public Result<String> closeQuestion(
            @Valid @RequestBody CloseQuestionRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        articleQuestionService.closeQuestion(request.getArticleId(), loginUser.getId());
        return Result.success("问题已关闭");
    }

    /** 查询问答帖当前最佳答案。 */
    @GetMapping("/acceptedAnswer")
    public Result<QuestionAnswerVO> getAcceptedAnswer(
            @RequestParam Long articleId,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(articleQuestionService.queryAcceptedAnswer(articleId, loginUserId));
    }
}
