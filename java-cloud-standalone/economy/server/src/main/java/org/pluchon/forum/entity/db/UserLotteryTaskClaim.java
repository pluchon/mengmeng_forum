package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

// 卡池任务每日领取记录
@Data
@TableName("user_lottery_task_claim")
public class UserLotteryTaskClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private String taskCode;

    private LocalDate claimDate;

    private Integer voucherGranted;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
