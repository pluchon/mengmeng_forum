package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "全站公开近期中奖摘要")
public class LotteryPublicRecentDrawVO {

    private String nickname;


    private String prizeName;

    private Long createTimeMillis;
}
