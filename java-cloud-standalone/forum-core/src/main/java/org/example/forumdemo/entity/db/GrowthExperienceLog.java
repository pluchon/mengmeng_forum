package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 成长经验流水
@Data
@TableName("growth_experience_log")
public class GrowthExperienceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sourceType;
    private Long sourceBusinessId;
    private Integer experienceDelta;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
