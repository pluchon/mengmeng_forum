package org.pluchon.forum.entity.vo.creator;

import lombok.AllArgsConstructor;
import lombok.Data;

// 创作中心趋势点
@Data
@AllArgsConstructor
public class CreatorTrendPointVO {

    private String label;

    private Integer readCount;

    private Integer likeCount;

    private Long followerCount;

    private Integer workCount;
}
