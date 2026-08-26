package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 萌币里程碑领取记录，一档只允许领取一次
@Data
@TableName("points_milestone_claim")
public class PointsMilestoneClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String milestoneCode;

    private Integer rewardAmount;

    private Integer deleteState;

    private Date createTime;

    private Date updateTime;
}
