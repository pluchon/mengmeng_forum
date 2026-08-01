package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

import java.util.Date;

// AI 会员长期偏好记忆
@Data
public class AiLongTermMemoryVO {

    private Long id;
    private Long sourceSessionId;
    private String memoryType;
    private String content;
    private Boolean enabled;
    private Date updateTime;
}
