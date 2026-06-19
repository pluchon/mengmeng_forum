package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.admin.AdminForumNoticeSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminForumNoticeUpdateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetNoticePinTopRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetNoticePublishStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminForumNoticeDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminForumNoticeRowVO;
import org.example.forumdemo.entity.vo.admin.AdminIdNameVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminForumNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理后台·论坛公告")
@RestController
@RequestMapping("/admin/content/notice")
public class AdminForumNoticeController {

    @Autowired
    private AdminForumNoticeService adminForumNoticeService;

    @Operation(summary = "公告分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminForumNoticeRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer noticeKind,
            @RequestParam(required = false) Long categoryScope,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer deleteState,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(adminForumNoticeService.pageNotices(page, size, pageNum, pageSize,
                noticeKind, categoryScope, title, deleteState, sortBy, sortOrder));
    }

    @Operation(summary = "公告详情")
    @GetMapping("/getDetail")
    public Result<AdminForumNoticeDetailVO> getDetail(@RequestParam Long id) {
        AdminForumNoticeDetailVO vo = adminForumNoticeService.getDetail(id);
        if (vo == null) {
            return Result.fail(ResultCode.FAILED_NOT_EXISTS);
        }
        return Result.success(vo);
    }

    @Operation(summary = "版规可选分类列表", description = "用于「适用范围」下拉，仅未删除分类；与 getList 同风格为 getCategories")
    @GetMapping({"/getCategories", "/categories"})
    public Result<List<AdminIdNameVO>> listNoticeCategories() {
        return Result.success(adminForumNoticeService.listCategoryOptions());
    }

    @Operation(summary = "新增公告")
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody AdminForumNoticeSaveRequest body) {
        adminForumNoticeService.save(body);
        return Result.success();
    }

    @Operation(summary = "更新公告")
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody AdminForumNoticeUpdateRequest body) {
        adminForumNoticeService.update(body);
        return Result.success();
    }

    @Operation(summary = "软删除公告")
    @PostMapping("/setDeleteState")
    public Result<Void> setDeleteState(@RequestBody AdminSetDeleteStateRequest body) {
        if (body.getId() == null || body.getDeleteState() == null) {
            return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        adminForumNoticeService.setDeleteState(body);
        return Result.success();
    }

    @Operation(summary = "上架/下架(草稿)")
    @PostMapping("/setPublishState")
    public Result<Void> setPublishState(@Valid @RequestBody AdminSetNoticePublishStateRequest body) {
        adminForumNoticeService.setPublishState(body);
        return Result.success();
    }

    @Operation(summary = "置顶/取消置顶")
    @PostMapping("/setPinTop")
    public Result<Void> setPinTop(@Valid @RequestBody AdminSetNoticePinTopRequest body) {
        adminForumNoticeService.setPinTop(body);
        return Result.success();
    }
}
