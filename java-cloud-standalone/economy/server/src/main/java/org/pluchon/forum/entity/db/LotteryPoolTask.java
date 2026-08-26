package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 卡池专属任务配置
@Data
@TableName("lottery_pool_task")
public class LotteryPoolTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String taskCode;

    private String title;

    private Integer targetCount;

    private Integer voucherReward;

    private Integer sortOrder;

    // 1启用 0停用
    private Integer enabled;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
