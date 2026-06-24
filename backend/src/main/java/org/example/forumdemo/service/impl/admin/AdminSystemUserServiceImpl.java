package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AdminPagination;
import org.example.forumdemo.entity.db.SysDept;
import org.example.forumdemo.entity.db.SysRole;
import org.example.forumdemo.entity.db.SysUserRole;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.admin.AdminSysUserRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.SysDeptMapper;
import org.example.forumdemo.mapper.SysRoleMapper;
import org.example.forumdemo.mapper.SysUserRoleMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.utils.PiiUtils;
import org.example.forumdemo.service.interfaces.admin.AdminSystemUserService;
import org.example.forumdemo.service.impl.user.JwtTokenVersionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminSystemUserServiceImpl implements AdminSystemUserService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private UserMapper userMapper;

    @Resource
    private SysDeptMapper sysDeptMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtTokenVersionService jwtTokenVersionService;

    @Override
    public PageResult<AdminSysUserRowVO> pageUsers(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                   String username, String status, String userFilter) {
        Page<User> p = AdminPagination.of(page, size, pageNum, pageSize);
        LambdaQueryWrapper<User> w = Wrappers.lambdaQuery(User.class);
        String filter = StringUtils.hasText(userFilter) ? userFilter.trim() : "";
        if ("deleted".equals(filter)) {
            w.eq(User::getDeleteState, (byte) 1);
        } else {
            w.ne(User::getDeleteState, (byte) 1);
            applyUserFilter(w, filter);
        }
        if (StringUtils.hasText(username)) {
            w.like(User::getUsername, username.trim());
        }
        if (StringUtils.hasText(status) && !StringUtils.hasText(userFilter)) {
            if ("1".equals(status)) {
                w.eq(User::getState, 0);
            } else if ("0".equals(status)) {
                w.eq(User::getState, 1);
            }
        }
        w.orderByDesc(User::getCreateTime);
        Page<User> result = userMapper.selectPage(p, w);
        List<AdminSysUserRowVO> rows = result.getRecords().stream().map(this::toRow).collect(Collectors.toList());
        long pages = result.getPages();
        return new PageResult<>(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize(),
                pages, result.hasNext());
    }

    @Override
    public AdminSysUserRowVO getDetail(String id) {
        User u = userMapper.selectById(Long.parseLong(id));
        if (u == null || (u.getDeleteState() != null && u.getDeleteState() == 1)) {
            return null;
        }
        return toRow(u);
    }

    @Override
    public void softDeleteUsers(List<String> ids) {
        for (String id : ids) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getDeleteState, (byte) 1)
                    .eq(User::getId, Long.parseLong(id)));
        }
    }

    @Override
    public void setUserMuted(Long operatorUserId, Long targetUserId, boolean muted) {
        if (operatorUserId != null && operatorUserId.equals(targetUserId) && muted) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "不能禁言自己"));
        }
        User u = userMapper.selectById(targetUserId);
        if (u == null || (u.getDeleteState() != null && u.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if ("admin".equalsIgnoreCase(u.getUsername())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "内置管理员不可禁言"));
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getState, muted ? (byte) 1 : (byte) 0)
                .eq(User::getId, targetUserId));
        if (muted) {
            jwtTokenVersionService.bump(targetUserId);
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + targetUserId);
    }

    @Override
    public void setForumAdmin(Long operatorUserId, Long targetUserId, boolean asAdmin) {
        User op = userMapper.selectById(operatorUserId);
        if (op == null || (op.getDeleteState() != null && op.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (op.getIsAdmin() == null || op.getIsAdmin() != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "需要论坛管理员身份"));
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null || (target.getDeleteState() != null && target.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS, "用户不存在或已删除"));
        }
        if ("admin".equalsIgnoreCase(target.getUsername()) && !asAdmin) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "内置管理员账号不可取消管理员权限"));
        }
        if (!asAdmin && operatorUserId.equals(targetUserId)) {
            long adminCount = userMapper.selectCount(Wrappers.lambdaQuery(User.class)
                    .eq(User::getDeleteState, (byte) 0)
                    .eq(User::getIsAdmin, (byte) 1));
            if (adminCount <= 1) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "至少保留一名论坛管理员"));
            }
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getIsAdmin, asAdmin ? (byte) 1 : (byte) 0)
                .eq(User::getId, targetUserId));
    }

    @Override
    public void updateRemark(Long targetUserId, String remark) {
        User u = userMapper.selectById(targetUserId);
        if (u == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        String text = remark != null ? remark.trim() : "";
        if (text.length() > 500) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "管理员标签最多 500 字"));
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getAdminTag, text.isEmpty() ? null : text)
                .eq(User::getId, targetUserId));
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + targetUserId);
    }

    private void applyUserFilter(LambdaQueryWrapper<User> w, String filter) {
        if (!StringUtils.hasText(filter)) {
            return;
        }
        switch (filter) {
            case "admin" -> w.eq(User::getIsAdmin, (byte) 1);
            case "member" -> w.gt(User::getVipTier, (byte) 0)
                    .and(q -> q.isNull(User::getVipExpireAt).or().gt(User::getVipExpireAt, new Date()));
            case "normal" -> w.and(q -> q.eq(User::getIsAdmin, (byte) 0).or().isNull(User::getIsAdmin))
                    .and(q -> q.eq(User::getVipTier, (byte) 0).or().isNull(User::getVipTier)
                            .or().le(User::getVipExpireAt, new Date()));
            case "muted" -> w.eq(User::getState, (byte) 1);
            case "unmuted" -> w.eq(User::getState, (byte) 0);
            default -> { }
        }
    }

    private AdminSysUserRowVO toRow(User u) {
        AdminSysUserRowVO vo = new AdminSysUserRowVO();
        vo.setId(String.valueOf(u.getId()));
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setGender(mapGender(u.getGender()));
        vo.setAvatar(u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
        vo.setEmail(PiiUtils.decrypt(u.getEmail()));
        vo.setPhone(PiiUtils.maskPhone(u.getPhoneNum()));
        vo.setStatus(u.getState() != null && u.getState() == 0 ? "1" : "0");
        vo.setType(Boolean.TRUE.equals(isBuiltInAdmin(u)) ? 1 : 2);
        vo.setDisabled(Boolean.TRUE.equals(isBuiltInAdmin(u)));
        vo.setDescription(u.getAdminTag() != null ? u.getAdminTag() : "");
        vo.setCreateUserString("系统");
        vo.setCreateTime(u.getCreateTime() != null ? DF.format(u.getCreateTime()) : "");
        vo.setDeptId(u.getDeptId() != null ? String.valueOf(u.getDeptId()) : "");
        if (u.getDeptId() != null) {
            SysDept d = sysDeptMapper.selectById(u.getDeptId());
            vo.setDeptName(d != null ? d.getName() : "");
        } else {
            vo.setDeptName("");
        }
        List<SysUserRole> urs = sysUserRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                .eq(SysUserRole::getUserId, u.getId()));
        List<String> roleCodes = new ArrayList<>();
        List<String> roleIdStrs = new ArrayList<>();
        StringBuilder roleNames = new StringBuilder();
        for (SysUserRole ur : urs) {
            SysRole r = sysRoleMapper.selectById(ur.getRoleId());
            if (r != null) {
                roleCodes.add(r.getRoleCode());
                roleIdStrs.add(String.valueOf(r.getId()));
                if (!roleNames.isEmpty()) {
                    roleNames.append(",");
                }
                roleNames.append(r.getRoleName());
            }
        }
        vo.setRoleIds(roleIdStrs);
        vo.setRoleNames(roleNames.toString());
        vo.setPermissions(List.of());
        vo.setForumAdmin(u.getIsAdmin() != null && u.getIsAdmin() == 1);
        vo.setVipTier(u.getVipTier() != null ? u.getVipTier().intValue() : 0);
        vo.setVipExpireAt(u.getVipExpireAt() != null ? DF.format(u.getVipExpireAt()) : null);
        vo.setDeleteState(u.getDeleteState() != null ? u.getDeleteState().intValue() : 0);
        return vo;
    }

    private boolean isBuiltInAdmin(User u) {
        return "admin".equalsIgnoreCase(u.getUsername());
    }

    /** forum.gender: 0女 1男 2保密 → Gi：1男 2女 3保密 */
    private String mapGender(Byte g) {
        if (g == null) {
            return "3";
        }
        return switch (g) {
            case 1 -> "1";
            case 0 -> "2";
            default -> "3";
        };
    }
}
