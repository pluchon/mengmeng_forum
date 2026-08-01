package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户端可选抽奖活动摘要")
public class LotteryActivityListItemVO {

    private Long id;

    private String title;

    private String coverImageUrl;

    private Integer costPointsPerDraw;
}
