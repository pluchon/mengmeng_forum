package org.example.forumdemo.entity.vo.driftbottle;

import lombok.Data;

// 漂流瓶今日额度响应
@Data
public class DriftBottleQuotaVO {

    // 今日剩余扔瓶次数
    private Integer createRemaining;

    // 今日剩余捞瓶次数
    private Integer pickRemaining;

    // 今日剩余评论次数
    private Integer commentRemaining;
}
