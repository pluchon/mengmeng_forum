package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.math.BigDecimal;

@Data
@TableName("forum_ai_usage_log")
public class ForumAiUsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("feature_code")
    private String featureCode;

    @TableField("model_code")
    private String modelCode;

    @TableField("input_tokens")
    private Integer inputTokens;

    @TableField("output_tokens")
    private Integer outputTokens;

    @TableField("image_count")
    private Integer imageCount;

    @TableField("points_cost")
    private Integer pointsCost;

    private Byte estimated;

    @TableField("billable_state")
    private Byte billableState;

    @TableField("cost_yuan")
    private BigDecimal costYuan;

    @TableField("quota_period_key")
    private String quotaPeriodKey;

    @TableField("related_id")
    private String relatedId;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;
}
