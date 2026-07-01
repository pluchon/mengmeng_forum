package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 群聊成员表
@Data
@TableName("group_chat_member")
public class GroupChatMember {

    // 成员记录 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 群聊 ID
    private Long groupId;

    // 用户 ID
    private Long userId;

    // 角色: 0群主 1成员
    private Byte role;

    // 群内备注昵称
    private String remarkName;

    // 禁言截止时间
    private Date muteUntil;

    // 最后已读群消息 ID
    private Long lastReadMessageId;

    // 加入时间
    private Date joinTime;

    // 状态: 0正常 1已退出 2被移除
    private Byte status;

    // 是否删除: 0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
