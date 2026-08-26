package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// AI 模型调用按日汇总，对应 forum_ai_model_usage_daily
@Data
@TableName("forum_ai_model_usage_daily")
public class ForumAiModelUsageDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("stat_date")
    private Date statDate;

    @TableField("model_code")
    private String modelCode;

    @TableField("call_count")
    private Integer callCount;

    @TableField("points_spent")
    private Long pointsSpent;

    @TableField("input_tokens")
    private Long inputTokens;

    @TableField("output_tokens")
    private Long outputTokens;

    @TableField("image_count")
    private Integer imageCount;
}
