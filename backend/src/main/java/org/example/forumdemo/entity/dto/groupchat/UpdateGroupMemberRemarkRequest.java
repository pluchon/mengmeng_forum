package org.example.forumdemo.entity.dto.groupchat;

import lombok.Data;

// 修改群内备注请求
@Data
public class UpdateGroupMemberRemarkRequest {

    // 群内备注昵称
    private String remarkName;
}
