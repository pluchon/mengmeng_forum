package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 漂流瓶主表
@Data
@TableName("drift_bottle")
public class DriftBottle {

    // 漂流瓶 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 真实作者用户 ID
    private Long userId;

    // 瓶子内容
    private String content;

    // 心情标签
    private String moodType;

    // 状态: 0可见 1隐藏 2删除
    private Byte status;

    // 评论数量
    private Integer commentCount;

    // 被捞次数
    private Integer pickedCount;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
