package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 用户成长档案
@Data
@TableName("user_growth_profile")
public class UserGrowthProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Byte formalState;
    private Integer experience;
    private Integer growthLevel;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
