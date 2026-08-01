package org.pluchon.forum.entity.dto.groupchat;

import lombok.Data;

// 修改群内备注请求
@Data
public class UpdateGroupMemberRemarkRequest {

    // 群内备注昵称
    private String remarkName;

    // 提醒模式: 0正常 1仅@提醒 2完全不提醒
    private Byte notifyMode;
}
