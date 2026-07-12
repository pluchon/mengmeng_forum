package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 成长挑战作答记录
@Data
@TableName("growth_challenge_attempt")
public class GrowthChallengeAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long challengeId;
    private Integer attemptNo;
    private String status;
    private String questionIdsJson;
    private String answersJson;
    private Integer score;
    private Date startedAt;
    private Date submittedAt;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
