package org.example.forum.api.economy;

// 跨服务 VIP 配额提示（纯契约 VO，无业务依赖）
public class VipQuotaHintVO {

    private Integer percent;
    private Boolean canUsePointsPay;
    private String quotaLabel;

    public Integer getPercent() {
        return percent;
    }

    public void setPercent(Integer percent) {
        this.percent = percent;
    }

    public Boolean getCanUsePointsPay() {
        return canUsePointsPay;
    }

    public void setCanUsePointsPay(Boolean canUsePointsPay) {
        this.canUsePointsPay = canUsePointsPay;
    }

    public String getQuotaLabel() {
        return quotaLabel;
    }

    public void setQuotaLabel(String quotaLabel) {
        this.quotaLabel = quotaLabel;
    }
}
