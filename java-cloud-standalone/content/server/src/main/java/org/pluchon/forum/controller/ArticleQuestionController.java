package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.AcceptQuestionAnswerRequest;
import org.pluchon.forum.entity.dto.article.SetQuestionResolvedRequest;
import org.pluchon.forum.entity.vo.article.QuestionAnswerVO;
import org.pluchon.forum.service.interfaces.article.ArticleQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 问答帖状态与采纳接口 关闭问题已移除
@Tag(name = "问答帖子", description = "问答解决状态与多条采纳")
@RestController
@RequestMapping("/articleQuestion")
public class ArticleQuestionController {

    @Autowired
    private ArticleQuestionService articleQuestionService;

    /** 采纳一条回答 一级或楼中楼 ；不改变问题解决状态，可多次采纳 */
    @PostMapping("/acceptAnswer")
    public Result<String> acceptAnswer(
            @Valid @RequestBody AcceptQuestionAnswerRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        articleQuestionService.acceptAnswer(
                request.getArticleId(),
                request.getReplyId(),
                request.getSubReplyId(),
                loginUser.getId());
        return Result.success("已采纳");
    }

    /** 作者切换已解决 / 未解决 */
    @PostMapping("/setResolved")
    public Result<String> setResolved(
            @Valid @RequestBody SetQuestionResolvedRequest request,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        boolean resolved = Boolean.TRUE.equals(request.getResolved());
        articleQuestionService.setResolved(request.getArticleId(), resolved, loginUser.getId());
        return Result.success(resolved ? "已标记为已解决" : "已标记为未解决");
    }

    /** 查询问答帖代表采纳回答 兼容旧客户端 */
    @GetMapping("/acceptedAnswer")
    public Result<QuestionAnswerVO> getAcceptedAnswer(
            @RequestParam Long articleId,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        return Result.success(articleQuestionService.queryAcceptedAnswer(articleId, loginUserId));
    }
}
