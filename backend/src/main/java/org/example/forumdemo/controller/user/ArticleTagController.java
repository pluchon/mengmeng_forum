package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.article.ArticleTagFeedbackRequest;
import org.example.forumdemo.entity.vo.article.ArticleTagVO;
import org.example.forumdemo.service.interfaces.article.ArticleTagService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "帖子标签")
@RestController
@RequestMapping("/article/tag")
public class ArticleTagController {

    @Resource
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
        String snippet = content != null && content.length() > 200 ? content.substring(0, 200) : content;
        return Result.success(articleTagService.suggestTags(boardId, title, snippet));
    }

    @Operation(summary = "申请新标签（AI 审核通过后入库并站内信通知）")
    @PostMapping("/feedback")
    public Result<Map<String, Object>> feedback(
            @RequestBody ArticleTagFeedbackRequest body,
            HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        Long boardId = body == null ? null : body.getBoardId();
        String name = body == null || body.getProposedName() == null ? "" : body.getProposedName();
        Long tagId = articleTagService.submitTagFeedback(user.getId(), boardId, name);
        return Result.success(Map.of("tagId", tagId, "message", "标签已通过审核，可在列表中选用"));
    }
}
