package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 幸运收集册里程奖励配置
@Data
@TableName("lottery_collect_milestone")
public class LotteryCollectMilestone {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer thresholdCount;

    private String rewardType;

    private Integer rewardValue;

    private Integer altRewardValue;

    private String label;

    private Integer sortOrder;

    private Integer enabled;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
