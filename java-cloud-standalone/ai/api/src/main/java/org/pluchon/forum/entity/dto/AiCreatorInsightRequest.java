package org.pluchon.forum.entity.dto;

import lombok.Data;

// 创作者数据小结请求，仅包含后端汇总后的统计数据
@Data
public class AiCreatorInsightRequest {

    private Long userId;

    private String clientRequestId;

    private String periodLabel;

    private String startDate;

    private String endDate;

    private Integer readCount;

    private Integer previousReadCount;

    private Integer likeCount;

    private Integer previousLikeCount;

    private Integer workCount;

    private Integer previousWorkCount;

    private Long newFollowerCount;

    private Long previousNewFollowerCount;

    private Long totalFollowerCount;
}
