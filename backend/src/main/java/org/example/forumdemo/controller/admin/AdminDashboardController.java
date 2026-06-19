package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.admin.AdminWorkbenchVO;
import org.example.forumdemo.service.interfaces.admin.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台·工作台")
@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Operation(summary = "工作台汇总", description = "帖子/用户/互动统计、公告预览、AI调用趋势、当前管理员展示信息")
    @GetMapping("/workbench")
    public Result<AdminWorkbenchVO> workbench(HttpServletRequest request) {
        User u = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(adminDashboardService.workbench(u.getId()));
    }
}
