package org.pluchon.forum.api.economy;

import java.util.Date;

// VIP 档位快照（跨服务只读）
public class VipTierSnapshotVO {

    private Byte vipTier;
    private Date vipExpireAt;
    private boolean vipActive;

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
}
