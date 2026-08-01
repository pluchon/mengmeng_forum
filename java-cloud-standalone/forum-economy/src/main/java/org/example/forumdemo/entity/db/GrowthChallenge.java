package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 成长挑战定义
@Data
@TableName("growth_challenge")
public class GrowthChallenge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String challengeCode;
    private String challengeType;
    private String title;
    private String description;
    private Long bankId;
    private Integer questionCount;
    private Integer passingScore;
    private Integer maxAttemptsPerDay;
    private Integer experienceReward;
    private Byte enabled;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
