package org.pluchon.forum.common.security;

import lombok.Data;

import java.util.Date;

// 仅用于请求鉴权上下文的用户主体，禁止承载密码、手机号等账户实体字段
@Data
public class AuthenticatedUser {

    private Long id;

    private String username;

    private Byte isAdmin;

    private Byte creatorState;

    private Byte state;

    private Byte vipTier;

    private Date vipExpireAt;
}
