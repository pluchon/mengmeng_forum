package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.admin.AdminRouteVO;
import org.example.forumdemo.entity.vo.admin.MenuOptVO;
import org.example.forumdemo.service.interfaces.admin.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理后台·菜单")
@RestController
@RequestMapping("/admin/system/menu")
public class AdminSystemMenuController {

    @Autowired
    private AdminAuthService adminAuthService;

    @Operation(summary = "菜单树（管理）")
    @GetMapping("/getList")
    public Result<List<AdminRouteVO>> getList() {
        return Result.success(adminAuthService.listAllMenusForAdminUi());
    }

    @Operation(summary = "菜单下拉树（简化）")
    @GetMapping("/getMenuOptions")
    public Result<List<MenuOptVO>> getMenuOptions() {
        List<AdminRouteVO> full = adminAuthService.listAllMenusForAdminUi();
        return Result.success(strip(full));
    }

    private List<MenuOptVO> strip(List<AdminRouteVO> nodes) {
        List<MenuOptVO> out = new ArrayList<>();
        if (nodes == null) {
            return out;
        }
        for (AdminRouteVO n : nodes) {
            MenuOptVO m = new MenuOptVO();
            m.setId(n.getId());
            m.setTitle(n.getTitle());
            m.setChildren(strip(n.getChildren()));
            out.add(m);
        }
        return out;
    }
}
