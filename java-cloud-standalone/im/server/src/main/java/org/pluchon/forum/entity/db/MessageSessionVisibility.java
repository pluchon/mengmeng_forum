package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 私信会话在单个用户视角下的显示状态
@Data
@TableName("message_session_visibility")
public class MessageSessionVisibility {

    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 状态所属用户
    private Long userId;

    // 私信对方用户
    private Long peerUserId;

    // 是否在会话列表隐藏: 0否 1是
    private Byte hiddenState;

    // 置顶时刻；NULL 表示未置顶。用一个字段同时表达「是否置顶」与「置顶先后」，
    // 不会出现布尔与时间彼此矛盾的状态
    private Date pinnedAt;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 逻辑删除: 0否 1是
    private Byte deleteState;
}
