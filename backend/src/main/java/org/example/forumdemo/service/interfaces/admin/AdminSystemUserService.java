package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.vo.admin.AdminSysUserRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

public interface AdminSystemUserService {

    PageResult<AdminSysUserRowVO> pageUsers(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                            String username, String status, String userFilter);

    AdminSysUserRowVO getDetail(String id);

    void softDeleteUsers(List<String> ids);

    /** 禁言 / 解禁：user.state 0 正常 1 禁言；内置 admin 账号不允许被禁言 */
    void setUserMuted(Long operatorUserId, Long targetUserId, boolean muted);

    /**
     * 设置用户论坛管理员标识（user.is_admin）。仅当前登录管理员可调用；
     * 内置 admin 账号不可被取消管理员；不可在仅剩一名管理员时取消自己。
     */
    void setForumAdmin(Long operatorUserId, Long targetUserId, boolean asAdmin);

    void updateRemark(Long targetUserId, String remark);
}
