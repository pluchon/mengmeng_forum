package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.admin.AdminDeleteUsersRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetForumAdminRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetUserMuteRequest;
import org.example.forumdemo.entity.dto.admin.AdminUpdateUserRemarkRequest;
import org.example.forumdemo.entity.vo.admin.AdminSysUserRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminSystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理后台·系统用户")
@RestController
@RequestMapping("/admin/system/user")
public class AdminSystemUserController {

    @Autowired
    private AdminSystemUserService adminSystemUserService;

    @Operation(summary = "用户分页列表")
    @GetMapping("/getList")
    public Result<PageResult<AdminSysUserRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userFilter) {
        return Result.success(adminSystemUserService.pageUsers(page, size, pageNum, pageSize, username, status, userFilter));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/getDetail")
    public Result<AdminSysUserRowVO> getDetail(@RequestParam String id) {
        AdminSysUserRowVO vo = adminSystemUserService.getDetail(id);
        if (vo == null) {
            return Result.fail(ResultCode.FAILED_NOT_EXISTS);
        }
        return Result.success(vo);
    }

    @Operation(summary = "禁言/解禁用户")
    @PostMapping("/setMute")
    public Result<Void> setMute(@RequestBody AdminSetUserMuteRequest body, HttpServletRequest request) {
        if (body.getId() == null || body.getMuted() == null) {
            return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        User sessionUser = (User) request.getAttribute(Constant.USER_SESSION);
        Long operatorId = sessionUser != null ? sessionUser.getId() : null;
        adminSystemUserService.setUserMuted(operatorId, body.getId(), body.getMuted());
        return Result.success();
    }

    @Operation(summary = "设置论坛管理员", description = "调整 user.is_admin；仅管理员可操作，内置 admin 不可被取消")
    @PostMapping("/setForumAdmin")
    public Result<Void> setForumAdmin(@Valid @RequestBody AdminSetForumAdminRequest body, HttpServletRequest request) {
        User sessionUser = (User) request.getAttribute(Constant.USER_SESSION);
        adminSystemUserService.setForumAdmin(sessionUser.getId(), body.getId(), body.getIsAdmin() == 1);
        return Result.success();
    }

    @Operation(summary = "更新用户备注")
    @PostMapping("/updateRemark")
    public Result<Void> updateRemark(@RequestBody AdminUpdateUserRemarkRequest body) {
        if (body.getId() == null) {
            return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        adminSystemUserService.updateRemark(body.getId(), body.getRemark());
        return Result.success();
    }

    @Operation(summary = "删除用户（软删除）")
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody AdminDeleteUsersRequest body) {
        List<String> ids = body != null ? body.getIds() : null;
        if (ids == null || ids.isEmpty()) {
            return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        adminSystemUserService.softDeleteUsers(ids);
        return Result.success(true);
    }
}
