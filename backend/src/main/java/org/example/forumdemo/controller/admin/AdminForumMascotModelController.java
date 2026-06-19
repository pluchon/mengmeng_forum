package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.admin.AdminForumMascotModelSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminForumMascotShelfRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminForumMascotModelRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminForumMascotModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台·看板娘模型")
@RestController
@RequestMapping("/admin/content/mascot-model")
public class AdminForumMascotModelController {

    @Autowired
    private AdminForumMascotModelService adminForumMascotModelService;

    @Operation(summary = "模型分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminForumMascotModelRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer shelfStatus,
            @RequestParam(required = false) Integer deleteState) {
        return Result.success(adminForumMascotModelService.pageModels(page, size, pageNum, pageSize,
                keyword, shelfStatus, deleteState));
    }

    @Operation(summary = "保存模型")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody AdminForumMascotModelSaveRequest body) {
        return Result.success(adminForumMascotModelService.save(body));
    }

    @Operation(summary = "上下架")
    @PostMapping("/setShelfStatus")
    public Result<Void> setShelfStatus(@RequestBody AdminForumMascotShelfRequest body) {
        adminForumMascotModelService.setShelfStatus(body);
        return Result.success();
    }

    @Operation(summary = "软删/恢复")
    @PostMapping("/setDeleteState")
    public Result<Void> setDeleteState(@RequestBody AdminSetDeleteStateRequest body) {
        adminForumMascotModelService.setDeleteState(body);
        return Result.success();
    }
}
