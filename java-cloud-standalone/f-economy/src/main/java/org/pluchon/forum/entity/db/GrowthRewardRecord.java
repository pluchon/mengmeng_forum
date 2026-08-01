package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 成长奖励流水
@Data
@TableName("growth_reward_record")
public class GrowthRewardRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long challengeId;
    private String rewardType;
    private String rewardValue;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
