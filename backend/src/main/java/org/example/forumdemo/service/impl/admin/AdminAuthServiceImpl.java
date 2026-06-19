package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.example.forumdemo.entity.db.SysMenu;
import org.example.forumdemo.entity.db.SysRole;
import org.example.forumdemo.entity.db.SysRoleMenu;
import org.example.forumdemo.entity.db.SysUserRole;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.admin.AdminRouteVO;
import org.example.forumdemo.entity.vo.admin.AdminSessionUserVO;
import org.example.forumdemo.mapper.SysMenuMapper;
import org.example.forumdemo.mapper.SysRoleMapper;
import org.example.forumdemo.mapper.SysRoleMenuMapper;
import org.example.forumdemo.mapper.SysUserRoleMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.admin.AdminAuthService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String SUPER_ROLE_CODE = "role_admin";

    @Resource
    private UserMapper userMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Resource
    private SysMenuMapper sysMenuMapper;

    @Override
    public AdminSessionUserVO buildSessionUser(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        List<Long> roleIds = sysUserRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty() && u.getIsAdmin() != null && u.getIsAdmin() == 1) {
            SysRole admin = sysRoleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class)
                    .eq(SysRole::getRoleCode, SUPER_ROLE_CODE));
            if (admin != null) {
                roleIds = List.of(admin.getId());
            }
        }
        List<String> roleCodes = roleIds.stream()
                .map(rid -> {
                    SysRole r = sysRoleMapper.selectById(rid);
                    return r != null ? r.getRoleCode() : null;
                })
                .filter(Objects::nonNull)
                .toList();

        AdminSessionUserVO vo = new AdminSessionUserVO();
        vo.setId(String.valueOf(u.getId()));
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
        vo.setRoles(roleCodes);
        vo.setPermissions(resolvePermissions(roleIds));
        return vo;
    }

    @Override
    public List<AdminRouteVO> buildRoutes(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return List.of();
        }
        List<Long> roleIds = sysUserRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty() && u.getIsAdmin() != null && u.getIsAdmin() == 1) {
            SysRole admin = sysRoleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class)
                    .eq(SysRole::getRoleCode, SUPER_ROLE_CODE));
            if (admin != null) {
                roleIds = List.of(admin.getId());
            }
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<String> roleCodes = roleIds.stream()
                .map(rid -> {
                    SysRole r = sysRoleMapper.selectById(rid);
                    return r != null ? r.getRoleCode() : null;
                })
                .filter(Objects::nonNull)
                .toList();

        List<String> menuIds = sysRoleMenuMapper.selectList(Wrappers.lambdaQuery(SysRoleMenu.class)
                        .in(SysRoleMenu::getRoleId, roleIds)).stream()
                .map(SysRoleMenu::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> menus = sysMenuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class)
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, "1")
                .orderByAsc(SysMenu::getSort));
        return buildVoTree(menus, roleCodes);
    }

    @Override
    public List<AdminRouteVO> listAllMenusForAdminUi() {
        List<SysMenu> menus = sysMenuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class)
                .eq(SysMenu::getStatus, "1")
                .orderByAsc(SysMenu::getSort));
        return buildVoTree(menus, List.of("role_admin", "role_user"));
    }

    private List<String> resolvePermissions(List<Long> roleIds) {
        boolean superRole = roleIds.stream().anyMatch(rid -> {
            SysRole r = sysRoleMapper.selectById(rid);
            return r != null && SUPER_ROLE_CODE.equals(r.getRoleCode());
        });
        if (superRole) {
            return List.of("*:*:*");
        }
        List<String> menuIds = sysRoleMenuMapper.selectList(Wrappers.lambdaQuery(SysRoleMenu.class)
                        .in(SysRoleMenu::getRoleId, roleIds)).stream()
                .map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> menus = sysMenuMapper.selectList(Wrappers.lambdaQuery(SysMenu.class).in(SysMenu::getId, menuIds));
        List<String> perms = menus.stream()
                .map(SysMenu::getPermission)
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        return perms.isEmpty() ? List.of("*:*:*") : perms;
    }

    private List<AdminRouteVO> buildVoTree(List<SysMenu> menus, List<String> roleCodes) {
        Map<String, AdminRouteVO> map = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            map.put(m.getId(), toVo(m, roleCodes));
        }
        List<AdminRouteVO> roots = new ArrayList<>();
        for (SysMenu m : menus) {
            AdminRouteVO vo = map.get(m.getId());
            String pid = m.getParentId();
            if (pid == null || pid.isEmpty()) {
                roots.add(vo);
            } else {
                AdminRouteVO parent = map.get(pid);
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(vo);
                }
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<AdminRouteVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(AdminRouteVO::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (AdminRouteVO n : nodes) {
            sortTree(n.getChildren());
        }
    }

    private AdminRouteVO toVo(SysMenu m, List<String> roleCodes) {
        AdminRouteVO vo = new AdminRouteVO();
        vo.setId(m.getId());
        vo.setParentId(m.getParentId());
        vo.setPath(m.getPath());
        vo.setComponent(m.getComponent());
        vo.setRedirect(m.getRedirect());
        vo.setType(m.getType());
        vo.setTitle(m.getTitle());
        vo.setIcon(m.getIcon() != null ? m.getIcon() : "");
        vo.setSort(m.getSort());
        vo.setHidden(m.getHidden() != null && m.getHidden() == 1);
        vo.setKeepAlive(m.getKeepAlive() != null && m.getKeepAlive() == 1);
        vo.setBreadcrumb(m.getBreadcrumb() == null || m.getBreadcrumb() == 1);
        vo.setAffix(m.getAffix() != null && m.getAffix() == 1);
        vo.setShowInTabs(m.getShowInTabs() == null || m.getShowInTabs() == 1);
        vo.setAlwaysShow(m.getAlwaysShow() != null && m.getAlwaysShow() == 1);
        vo.setActiveMenu(m.getActiveMenu() != null ? m.getActiveMenu() : "");
        vo.setPermission(m.getPermission() != null ? m.getPermission() : "");
        vo.setStatus(m.getStatus());
        vo.setRoles(new ArrayList<>(roleCodes));
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
