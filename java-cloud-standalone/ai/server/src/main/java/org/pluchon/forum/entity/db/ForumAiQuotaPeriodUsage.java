package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户AI周期额度的已用量与并发预占汇总
@Data
@TableName("forum_ai_quota_period_usage")
public class ForumAiQuotaPeriodUsage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String quotaPeriodKey;
    private Long qwenUsedMicros;
    private Long qwenReservedMicros;
    private Integer wanUsedCount;
    private Integer wanReservedCount;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
