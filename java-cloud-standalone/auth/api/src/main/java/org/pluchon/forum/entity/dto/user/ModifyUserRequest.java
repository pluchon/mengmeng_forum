package org.pluchon.forum.entity.dto.user;

import lombok.Data;

// 修改用户信息请求
@Data
public class ModifyUserRequest {
    private String userName;
    private String nickName;
    private String email;
    private Byte gender;
    private String phoneNum;
    // 个人简介
    private String remark;
}
