package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户近期单次抽奖记录摘要（展示条）")
public class LotteryRecentDrawVO {

    private String prizeName;

    // 1：来自带 batchKey 的多抽批次 当前仅有十连 ；0：单抽
    @Schema(description = "是否属于十连批次中的一抽")
    private Integer multiDraw;
}
