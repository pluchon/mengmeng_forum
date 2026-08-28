package org.pluchon.forum.api.economy;

import lombok.Getter;
import lombok.Setter;

// 跨服务 VIP 配额提示 纯契约 VO，无业务依赖
@Setter
@Getter
public class VipQuotaHintVO {

    private Integer percent;
    private Boolean canUsePointsPay;
    private String quotaLabel;

}
