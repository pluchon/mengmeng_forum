package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.admin.AdminSetArticleStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminArticlePreviewVO;
import org.example.forumdemo.entity.vo.admin.AdminArticleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminContentArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台·帖子")
@RestController
@RequestMapping("/admin/content/article")
public class AdminContentArticleController {

    @Autowired
    private AdminContentArticleService adminContentArticleService;

    @Operation(summary = "帖子分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminArticleRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long boardId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) Integer deleteState) {
        return Result.success(adminContentArticleService.pageArticles(page, size, pageNum, pageSize,
                title, boardId, status, state, deleteState));
    }

    @Operation(summary = "帖子只读预览（正文+相册）")
    @GetMapping("/preview")
    public Result<AdminArticlePreviewVO> preview(@RequestParam Long id) {
        return Result.success(adminContentArticleService.previewArticle(id));
    }

    @Operation(summary = "设置帖子删除标记")
    @PostMapping("/setDeleteState")
    public Result<Void> setDeleteState(@RequestBody AdminSetDeleteStateRequest body) {
        adminContentArticleService.setDeleteState(body);
        return Result.success();
    }

    @Operation(summary = "设置帖子审核禁用状态(state)")
    @PostMapping("/setState")
    public Result<Void> setState(@RequestBody AdminSetArticleStateRequest body) {
        adminContentArticleService.setState(body);
        return Result.success();
    }
}
