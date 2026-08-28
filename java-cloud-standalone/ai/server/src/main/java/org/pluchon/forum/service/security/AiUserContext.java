package org.pluchon.forum.service.security;

import lombok.Data;

import java.util.Date;

// AI 域使用的只读用户上下文，不承载认证域实体
@Data
public class AiUserContext {

    private Long id;

    private String username;

    private String nickname;

    private String avatarUrl;

    private Byte isAdmin;

    private Byte creatorState;

    private Byte state;

    private Byte vipTier;

    private Date vipExpireAt;

    private boolean vipActive;

    // 基础配额档位
    private Byte baseQuotaTier;

    // 配额周期起点 由 economy 域权威维护 续期不移动
    private Date quotaPeriodStart;

    // 配额周期终点
    private Date quotaPeriodEnd;
}
