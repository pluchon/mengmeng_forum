package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

/**
 * 管理端论坛会员只读信息（非系统后台账号表）。
 */
@Data
public class AdminForumMemberPreviewVO {

    private String id;

    private String username;

    private String nickname;

    private Integer gender;

    private String avatarUrl;

    private Integer articleCount;

    private Integer points;

    private Integer vipTier;

    private String vipExpireAt;

    /** 0 正常 1 禁言 */
    private Integer state;

    private Integer isAdmin;

    private String createTime;
}
