package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 群聊主表
@Data
@TableName("group_chat")
public class GroupChat {

    // 群聊 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 群主用户 ID
    private Long ownerUserId;

    // 群名称
    private String name;

    // 群头像 URL
    private String avatarUrl;

    // 群简介
    private String intro;

    // 群类型: 0公开 1私有
    private Byte groupType;

    // 当前身份对应人数上限快照
    private Integer memberLimit;

    // 当前成员数
    private Integer memberCount;

    // 群状态: 0正常 1满员 2超额锁定 3已解散 4违规封禁
    private Byte status;

    // 是否删除: 0否 1是
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
