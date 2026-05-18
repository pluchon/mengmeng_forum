package org.example.forumdemo.entity.vo.user;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-06-17:35
 * 作者代码水平一般，难免难看，请见谅
 */
//用户登录响应
@Data
public class UserLoginResponse {
    //这些会从数据库中查询到结果
    private Integer userId;
    private String userName;
    private String nickname;
}
