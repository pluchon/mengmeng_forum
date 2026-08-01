package org.example.forumdemo.entity.vo.growth;

import lombok.Data;

import java.util.Date;

// 成长经验记录响应
@Data
public class GrowthExperienceRecordVO {
    private Long id;
    private String sourceType;
    private String sourceLabel;
    private Integer experienceDelta;
    private String remark;
    private Date createTime;
}
