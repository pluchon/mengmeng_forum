package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.util.List;

/**
 * Gi 系统管理「用户列表」行（参见 forum-vue-admin {@code apis/system/user ListItem}）。
 */
@Data
public class AdminSysUserRowVO {

    private String id;

    private String createUserString;

    private String createTime;

    private Boolean disabled;

    private String username;

    private String nickname;

    private String gender;

    private String avatar;

    private String email;

    private String phone;

    private String status;

    private Integer type;

    private String description;

    private List<String> roleIds;

    private String roleNames;

    private String deptId;

    private String deptName;

    private List<String> permissions;

    /** 论坛管理员 user.is_admin */
    private Boolean forumAdmin;

    /** VIP 档位 0普通 1PRO 2MAX */
    private Integer vipTier;

    private String vipExpireAt;

    /** 0 未删 1 已删 */
    private Integer deleteState;
}
