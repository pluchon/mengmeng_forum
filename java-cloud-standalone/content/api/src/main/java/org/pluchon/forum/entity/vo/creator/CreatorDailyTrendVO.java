package org.pluchon.forum.entity.vo.creator;

import lombok.AllArgsConstructor;
import lombok.Data;

// 创作中心单日阅读与点赞增量
@Data
@AllArgsConstructor
public class CreatorDailyTrendVO {

    private String statDate;
    private Integer readCount;
    private Integer likeCount;
}
