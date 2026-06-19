package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("lottery_draw_hourly_stat")
public class LotteryDrawHourlyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("activity_id")
    private Long activityId;

    @TableField("stat_hour")
    private Date statHour;

    @TableField("draw_count")
    private Integer drawCount;

    private Date createTime;

    private Date updateTime;
}
