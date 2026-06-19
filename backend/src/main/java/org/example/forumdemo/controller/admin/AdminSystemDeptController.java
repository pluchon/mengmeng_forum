package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.admin.DeptNodeVO;
import org.example.forumdemo.service.interfaces.admin.AdminSystemDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门树（Gi /system/dept/getList 返回数组树）。
 */
@Tag(name = "管理后台·部门")
@RestController
@RequestMapping("/admin/system/dept")
public class AdminSystemDeptController {

    @Autowired
    private AdminSystemDeptService adminSystemDeptService;

    @Operation(summary = "部门树列表")
    @GetMapping("/getList")
    public Result<List<DeptNodeVO>> getList() {
        return Result.success(adminSystemDeptService.listDeptTree());
    }
}
