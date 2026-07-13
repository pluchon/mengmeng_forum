package org.example.forumdemo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.AcceptQuestionAnswerRequest;
import org.example.forumdemo.entity.dto.article.CloseQuestionRequest;
import org.example.forumdemo.entity.vo.article.QuestionAnswerVO;
import org.example.forumdemo.service.interfaces.article.ArticleQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 问答帖状态与最佳答案接口
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
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
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
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
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
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(articleQuestionService.queryAcceptedAnswer(articleId, loginUserId));
    }
}
