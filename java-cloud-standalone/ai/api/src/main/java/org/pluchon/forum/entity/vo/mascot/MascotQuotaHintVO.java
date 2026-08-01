package org.pluchon.forum.entity.vo.mascot;

import lombok.Data;

// 看板娘模型配额使用率提示
@Data
public class MascotQuotaHintVO {

    private Integer percent;
    private Boolean canUsePointsPay;
    private String quotaLabel;
}
