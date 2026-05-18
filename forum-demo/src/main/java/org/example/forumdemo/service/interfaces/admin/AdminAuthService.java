package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.vo.admin.AdminRouteVO;
import org.example.forumdemo.entity.vo.admin.AdminSessionUserVO;

import java.util.List;

public interface AdminAuthService {

    AdminSessionUserVO buildSessionUser(Long userId);

    List<AdminRouteVO> buildRoutes(Long userId);

    /** 菜单管理页：返回完整菜单树（占位 roles 仅供前端展示） */
    List<AdminRouteVO> listAllMenusForAdminUi();
}
