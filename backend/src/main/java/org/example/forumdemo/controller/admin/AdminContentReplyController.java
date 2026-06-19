package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.admin.AdminSetArticleStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminArticleReplyRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminContentReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台·帖子回复(一级)")
@RestController
@RequestMapping("/admin/content/reply")
public class AdminContentReplyController {

    @Autowired
    private AdminContentReplyService adminContentReplyService;

    @Operation(summary = "一级回复分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminArticleReplyRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) String contentKeyword,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) Integer deleteState) {
        return Result.success(adminContentReplyService.pageReplies(page, size, pageNum, pageSize,
                articleId, contentKeyword, state, deleteState));
    }

    @PostMapping("/setDeleteState")
    public Result<Void> setDeleteState(@RequestBody AdminSetDeleteStateRequest body) {
        adminContentReplyService.setDeleteState(body);
        return Result.success();
    }

    @PostMapping("/setState")
    public Result<Void> setState(@RequestBody AdminSetArticleStateRequest body) {
        adminContentReplyService.setState(body);
        return Result.success();
    }
}
