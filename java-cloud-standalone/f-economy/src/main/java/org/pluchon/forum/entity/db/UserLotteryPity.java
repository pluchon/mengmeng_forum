package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 抽奖硬保底计数，对应 user_lottery_pity（economy 权威）
@Data
@TableName("user_lottery_pity")
public class UserLotteryPity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 距上次神秘大奖已连续开奖次数
    private Integer pityDraws;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleteState;
}
