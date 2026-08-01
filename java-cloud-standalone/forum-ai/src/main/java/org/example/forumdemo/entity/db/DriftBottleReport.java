package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 漂流瓶举报表
@Data
@TableName("drift_bottle_report")
public class DriftBottleReport {

    // 举报 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 目标类型: 0瓶子 1评论
    private Byte targetType;

    // 目标 ID
    private Long targetId;

    // 举报用户 ID
    private Long reportUserId;

    // 举报原因类型
    private String reasonType;

    // 举报补充说明
    private String reasonDetail;

    // 处理状态: 0待处理 1已处理 2已驳回
    private Byte status;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;

    // 是否删除: 0否 1是
    private Byte deleteState;
}
