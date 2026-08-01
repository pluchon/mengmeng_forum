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
}
