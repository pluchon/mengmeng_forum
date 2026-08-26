package org.pluchon.forum.entity.vo.user;

import lombok.Data;

import java.util.Date;

// 登录态用户对外展示 不含密码、盐值、哈希
@Data
public class UserSessionVO {

    private Long id;
    private String token;
    private String username;
    private String nickname;
    private String phoneNum;
    private String email;
    private Byte gender;
    private String avatarUrl;
    private String backgroundUrl;
    private Integer articleCount;
    private Byte isAdmin;
    // 积分余额来自 economy.points_wallet，登录/会话组装时填充
    private Integer points;
    private Byte vipTier;
    private Date vipExpireAt;
    private Long mascotModelId;
    private String remark;
    private String ipRegion;
    private Byte state;
    private Date createTime;
    private Date updateTime;
}
