package org.pluchon.forum.api.economy;

import java.util.Date;

// VIP 档位快照 跨服务只读
public class VipTierSnapshotVO {

    private Byte vipTier;
    private Date vipExpireAt;
    private boolean vipActive;
    private Byte baseQuotaTier;
    private Date quotaPeriodStart;
    private Date quotaPeriodEnd;

    public Byte getVipTier() {
        return vipTier;
    }

    public void setVipTier(Byte vipTier) {
        this.vipTier = vipTier;
    }

    public Date getVipExpireAt() {
        return vipExpireAt;
    }

    public void setVipExpireAt(Date vipExpireAt) {
        this.vipExpireAt = vipExpireAt;
    }

    public boolean isVipActive() {
        return vipActive;
    }

    public void setVipActive(boolean vipActive) {
        this.vipActive = vipActive;
    }

    public Byte getBaseQuotaTier() {
        return baseQuotaTier;
    }

    public void setBaseQuotaTier(Byte baseQuotaTier) {
        this.baseQuotaTier = baseQuotaTier;
    }

    public Date getQuotaPeriodStart() {
        return quotaPeriodStart;
    }

    public void setQuotaPeriodStart(Date quotaPeriodStart) {
        this.quotaPeriodStart = quotaPeriodStart;
    }

    public Date getQuotaPeriodEnd() {
        return quotaPeriodEnd;
    }

    public void setQuotaPeriodEnd(Date quotaPeriodEnd) {
        this.quotaPeriodEnd = quotaPeriodEnd;
    }
}
