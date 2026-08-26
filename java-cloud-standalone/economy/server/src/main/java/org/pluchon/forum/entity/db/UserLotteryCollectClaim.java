package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户幸运收集册里程领取记录
@Data
@TableName("user_lottery_collect_claim")
public class UserLotteryCollectClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private Integer thresholdCount;

    private String rewardType;

    private Integer rewardValue;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
