package org.example.forumdemo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AdminPagination;
import org.example.forumdemo.entity.vo.admin.AdminRoleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.admin.AdminSystemRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理后台·角色")
@RestController
@RequestMapping("/admin/system/role")
public class AdminSystemRoleController {

    @Autowired
    private AdminSystemRoleService adminSystemRoleService;

    @Operation(summary = "角色分页")
    @GetMapping("/getList")
    public Result<PageResult<AdminRoleRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        Page<?> p = AdminPagination.of(page, size, pageNum, pageSize);
        return Result.success(adminSystemRoleService.getList(p));
    }

    @Operation(summary = "角色已分配菜单 id 列表")
    @GetMapping("/getRoleMenuIds")
    public Result<List<String>> getRoleMenuIds(@RequestParam String role) {
        return Result.success(adminSystemRoleService.getRoleMenuIds(role));
    }
}
