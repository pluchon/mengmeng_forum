package org.pluchon.forum.entity.vo.vip;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单条配额用量")
public class VipQuotaItemVO {

    private String quotaKey;
    private String displayName;
    private String modelCode;
    private String iconProvider;
    private String quotaType;
    // 每日 | 本周期 | 会员期内 与后端计费维度一致
    private String scopeLabel;
    private String tierTag;
    private Long used;
    private Long limit;
    private String unit;
    private Integer percent;
    private String resetHint;
}
