package org.example.forum.api.auth;

import lombok.Data;

import java.util.Date;

// 用户域内部视图：仅含跨服务所需的非敏感字段，禁止暴露密码/盐/哈希等
@Data
public class UserInternalVO {

    // 用户 ID
    private Long id;

    // 登录用户名
    private String username;

    // 昵称
    private String nickname;

    // 头像 URL
    private String avatarUrl;

    // 主页背景图 URL
    private String backgroundUrl;

    // 发帖数量
    private Integer articleCount;

    // 是否管理员：0=否 1=是
    private Byte isAdmin;

    // 创作者认证状态：0=未认证 1=已认证
    private Byte creatorState;

    // 账号状态：0=正常 1=禁言
    private Byte state;

    // 性别：0女 1男 2保密
    private Byte gender;

    // 自我介绍
    private String remark;

    // 最近登录 IP 属地
    private String ipRegion;

    // 创建时间
    private Date createTime;
}
