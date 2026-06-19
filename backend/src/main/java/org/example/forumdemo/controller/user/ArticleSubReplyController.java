package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.SubReplyRequest;
import org.example.forumdemo.entity.vo.article.ArticleSubReplyListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.article.ArticleSubReplyService;
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
        User loginUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        articleSubReplyService.subReply(request, loginUser.getId());
        return Result.success("楼中楼回复成功");
    }

    @Operation(summary = "分页查询楼中楼列表", description = "传入一级回复ID(replyId)，返回该楼层下的所有子回复->分页")
    @GetMapping("/getSubReplyByReplyId")
    public Result<PageResult<ArticleSubReplyListResponse>> getSubReplyByReplyId(Long replyId,
        @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(articleSubReplyService.querySubReplyByReplyId(replyId, pageNum, pageSize));
    }
}
