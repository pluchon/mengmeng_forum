package org.pluchon.forum.api.economy;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

// VIP 档位快照 跨服务只读
@Setter
@Getter
public class VipTierSnapshotVO {

    private Byte vipTier;
    private Date vipExpireAt;
    private boolean vipActive;
    private Byte baseQuotaTier;
    private Date quotaPeriodStart;
    private Date quotaPeriodEnd;

}
