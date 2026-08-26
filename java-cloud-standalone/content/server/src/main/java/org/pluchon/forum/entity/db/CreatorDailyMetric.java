package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 创作者按日累计的内容互动数据
@Data
@TableName("creator_daily_metric")
public class CreatorDailyMetric {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Date statDate;
    private Integer readCount;
    private Integer likeCount;
    private Integer publishCount;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
