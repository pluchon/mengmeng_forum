package org.example.forumdemo.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.util.AdminPagination;
import org.example.forumdemo.entity.db.SysRole;
import org.example.forumdemo.entity.db.SysRoleMenu;
import org.example.forumdemo.entity.vo.admin.AdminRoleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.SysRoleMapper;
import org.example.forumdemo.mapper.SysRoleMenuMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "管理后台·角色")
@RestController
@RequestMapping("/admin/system/role")
public class AdminSystemRoleController {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Operation(summary = "角色分页")
    @GetMapping("/getList")
    public Result<PageResult<AdminRoleRowVO>> getList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        Page<SysRole> p = AdminPagination.of(page, size, pageNum, pageSize);
        Page<SysRole> result = sysRoleMapper.selectPage(p, Wrappers.lambdaQuery(SysRole.class).orderByAsc(SysRole::getId));
        var rows = result.getRecords().stream().map(this::toRow).collect(Collectors.toList());
        return Result.success(new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext()));
    }

    @Operation(summary = "角色已分配菜单 id 列表")
    @GetMapping("/getRoleMenuIds")
    public Result<List<String>> getRoleMenuIds(@RequestParam String role) {
        SysRole r = sysRoleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class).eq(SysRole::getRoleCode, role));
        if (r == null) {
            return Result.success(List.of());
        }
        List<String> ids = sysRoleMenuMapper.selectList(Wrappers.lambdaQuery(SysRoleMenu.class)
                        .eq(SysRoleMenu::getRoleId, r.getId())).stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
        return Result.success(ids);
    }

    private AdminRoleRowVO toRow(SysRole r) {
        AdminRoleRowVO vo = new AdminRoleRowVO();
        vo.setId(String.valueOf(r.getId()));
        vo.setName(r.getRoleName());
        vo.setCode(r.getRoleCode());
        vo.setSort(Math.toIntExact(r.getId()));
        vo.setStatus("1");
        vo.setType("role_admin".equals(r.getRoleCode()) ? 1 : 2);
        vo.setDisabled("role_admin".equals(r.getRoleCode()));
        vo.setDescription(r.getRemark() != null ? r.getRemark() : "");
        vo.setCreateUserString("系统");
        vo.setCreateTime(r.getCreateTime() != null ? DF.format(r.getCreateTime()) : "");
        return vo;
    }
}
