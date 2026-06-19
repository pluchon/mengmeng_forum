package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.entity.db.SysRole;
import org.example.forumdemo.entity.db.SysRoleMenu;
import org.example.forumdemo.entity.vo.admin.AdminRoleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.SysRoleMapper;
import org.example.forumdemo.mapper.SysRoleMenuMapper;
import org.example.forumdemo.service.interfaces.admin.AdminSystemRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminSystemRoleServiceImpl implements AdminSystemRoleService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public PageResult<AdminRoleRowVO> getList(Page<?> page) {
        Page<SysRole> rolePage = new Page<>(page.getCurrent(), page.getSize());
        Page<SysRole> result = sysRoleMapper.selectPage(rolePage,
                Wrappers.lambdaQuery(SysRole.class).orderByAsc(SysRole::getId));
        List<AdminRoleRowVO> rows = result.getRecords().stream()
                .map(this::toRow)
                .collect(Collectors.toList());
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                result.getPages(), result.hasNext());
    }

    @Override
    public List<String> getRoleMenuIds(String role) {
        SysRole r = sysRoleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class).eq(SysRole::getRoleCode, role));
        if (r == null) {
            return List.of();
        }
        return sysRoleMenuMapper.selectList(Wrappers.lambdaQuery(SysRoleMenu.class)
                        .eq(SysRoleMenu::getRoleId, r.getId())).stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
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
