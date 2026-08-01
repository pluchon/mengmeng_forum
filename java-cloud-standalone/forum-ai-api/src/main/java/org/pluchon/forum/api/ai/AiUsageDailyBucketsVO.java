package org.pluchon.forum.api.ai;

import java.util.HashMap;
import java.util.Map;

// AI 日用量 + 周期 token 汇总（跨服务契约 VO）
public class AiUsageDailyBucketsVO {

    private Integer qwenFlashUsed;
    private Integer advancedLlmUsed;
    private Integer imageNormalUsed;
    private Integer imagePremiumUsed;
    private Integer companionNormalUsed;
    private Integer companionPremiumUsed;
    private Integer totalCalls;
    private Map<String, Long> tokenByModel = new HashMap<>();

    public Integer getQwenFlashUsed() {
        return qwenFlashUsed;
    }

    public void setQwenFlashUsed(Integer qwenFlashUsed) {
        this.qwenFlashUsed = qwenFlashUsed;
    }

    public Integer getAdvancedLlmUsed() {
        return advancedLlmUsed;
    }

    public void setAdvancedLlmUsed(Integer advancedLlmUsed) {
        this.advancedLlmUsed = advancedLlmUsed;
    }

    public Integer getImageNormalUsed() {
        return imageNormalUsed;
    }

    public void setImageNormalUsed(Integer imageNormalUsed) {
        this.imageNormalUsed = imageNormalUsed;
    }

    public Integer getImagePremiumUsed() {
        return imagePremiumUsed;
    }

    public void setImagePremiumUsed(Integer imagePremiumUsed) {
        this.imagePremiumUsed = imagePremiumUsed;
    }

    public Integer getCompanionNormalUsed() {
        return companionNormalUsed;
    }

    public void setCompanionNormalUsed(Integer companionNormalUsed) {
        this.companionNormalUsed = companionNormalUsed;
    }

    public Integer getCompanionPremiumUsed() {
        return companionPremiumUsed;
    }

    public void setCompanionPremiumUsed(Integer companionPremiumUsed) {
        this.companionPremiumUsed = companionPremiumUsed;
    }

    public Integer getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(Integer totalCalls) {
        this.totalCalls = totalCalls;
    }

    public Map<String, Long> getTokenByModel() {
        return tokenByModel;
    }

    public void setTokenByModel(Map<String, Long> tokenByModel) {
        this.tokenByModel = tokenByModel == null ? new HashMap<>() : tokenByModel;
    }
}
