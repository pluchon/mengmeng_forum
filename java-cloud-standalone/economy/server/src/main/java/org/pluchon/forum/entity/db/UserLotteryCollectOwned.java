package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户幸运收集册已收集图标
@Data
@TableName("user_lottery_collect_owned")
public class UserLotteryCollectOwned {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private Integer iconId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
