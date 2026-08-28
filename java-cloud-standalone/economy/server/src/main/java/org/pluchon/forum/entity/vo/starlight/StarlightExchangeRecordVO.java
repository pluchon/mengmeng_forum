package org.pluchon.forum.entity.vo.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "萌星辉兑换记录")
public class StarlightExchangeRecordVO {

    private Long id;

    private Long itemId;

    private String itemName;

    private Integer pricePaid;

    private String rewardType;

    private Integer rewardValue;

    private String rewardSummary;

    // 使用状态：0 未使用 1 已使用
    private Integer useStatus;

    private Date useTime;

    private Byte actualGrantTier;

    private Integer actualDurationHours;

    // 商品标签快照展示 来自当前商品配置，缺失时为空
    private String tag;

    private Date createTime;
}
