package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("ai_usage_daily")
public class AiUsageDaily {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate usageDate;
    private Integer deepseekWriteUsed;
    private Integer advancedLlmUsed;
    private Integer imageNormalUsed;
    private Integer imagePremiumUsed;
    private Integer companionNormalUsed;
    private Integer companionPremiumUsed;
    private Integer coverHintUsed;
    private Byte deleteState;
    private Date createTime;
    private Date updateTime;
}
