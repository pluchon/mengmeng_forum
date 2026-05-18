package org.example.forumdemo.entity.vo.vip;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Schema(description = "VIP 配额面板（PRO/MAX）")
public class VipQuotaPanelVO {

    private Byte vipTier;
    private String tierLabel;
    private Date periodStart;
    private Date periodEnd;
    private Long totalTokensUsed;
    private Integer totalCalls;
    private List<VipQuotaGroupVO> groups;
    private String emptyHint;
}
