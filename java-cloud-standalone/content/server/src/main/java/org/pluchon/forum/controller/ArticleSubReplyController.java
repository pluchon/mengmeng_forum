package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.SubReplyRequest;
import org.pluchon.forum.entity.vo.article.ArticleSubReplyListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.article.ArticleSubReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "楼中楼回复模块", description = "针对一级回复的楼中楼增删查接口")
@RestController
@RequestMapping("/articleSubReply")
public class ArticleSubReplyController {

    @Autowired
    private ArticleSubReplyService articleSubReplyService;

    @Operation(summary = "发表楼中楼回复", description = "传入 articleId->帖子ID、replyId->楼层ID、replyUserId->被回复的用户ID、content->内容")
    @PutMapping("/subReply")
    public Result subReply(@RequestBody SubReplyRequest request, HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        articleSubReplyService.subReply(request, loginUser.getId());
        return Result.success("楼中楼回复成功");
    }

    @Operation(summary = "分页查询楼中楼列表", description = "传入一级回复ID(replyId)，返回该楼层下的所有子回复->分页")
    @GetMapping("/getSubReplyByReplyId")
    public Result<PageResult<ArticleSubReplyListResponse>> getSubReplyByReplyId(Long replyId,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "5") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser != null ? loginUser.getId() : null;
        return Result.success(articleSubReplyService.querySubReplyByReplyId(
                replyId, pageNum, pageSize, loginUserId));
    }
}
