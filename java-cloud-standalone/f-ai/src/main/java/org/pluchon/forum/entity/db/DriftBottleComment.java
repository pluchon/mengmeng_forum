package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 漂流瓶评论表
@Data
@TableName("drift_bottle_comment")
public class DriftBottleComment {

    // 评论 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 漂流瓶 ID
    private Long bottleId;

    // 真实评论用户 ID
    private Long userId;

    // 评论内容
    private String content;

    // 状态: 0可见 1隐藏 2删除
    private Byte status;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
