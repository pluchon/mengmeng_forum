package org.example.forumdemo.entity.vo.user;

import lombok.Data;

// 登录成功结果：JWT 与用户信息
@Data
public class AuthLoginResultVO {

    private String token;
    private UserSessionVO user;
}
