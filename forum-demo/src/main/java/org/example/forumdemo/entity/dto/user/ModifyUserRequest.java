package org.example.forumdemo.entity.dto.user;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-10-12:07
 * 作者代码水平一般，难免难看，请见谅
 */
//修改用户信息请求
@Data
public class ModifyUserRequest {
    private String userName;
    private String nickName;
    private String email;
    private Byte gender;
    private String phoneNum;
    //个人简介
    private String remark;
}
