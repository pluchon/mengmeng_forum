package org.example.forumdemo.converter;

import org.example.forumdemo.entity.vo.ai.AiUsageStatsVO;
import org.example.forumdemo.entity.vo.mascot.MascotChatResponseVO;
import org.example.forumdemo.entity.vo.mascot.MascotQuotaHintVO;

import java.util.Map;

// 看板娘响应转换
public final class MascotConverter {

    private MascotConverter() {
    }

    public static MascotQuotaHintVO toQuotaHintVO(Map<String, Object> raw) {
        MascotQuotaHintVO vo = new MascotQuotaHintVO();
        if (raw == null) {
            vo.setPercent(0);
            vo.setCanUsePointsPay(false);
            vo.setQuotaLabel("");
            return vo;
        }
        vo.setPercent(intVal(raw.get("percent")));
        Object canPay = raw.get("canUsePointsPay");
        vo.setCanUsePointsPay(canPay instanceof Boolean b ? b : Boolean.TRUE.equals(String.valueOf(canPay)));
        vo.setQuotaLabel(stringVal(raw.get("quotaLabel")));
        return vo;
    }

    public static MascotChatResponseVO toChatResponse(Map<String, Object> data) {
        MascotChatResponseVO vo = new MascotChatResponseVO();
        if (data == null) {
            return vo;
        }
        vo.setSessionId(stringVal(data.get("sessionId")));
        vo.setReply(stringVal(data.get("reply")));
        vo.setImageUrl(stringVal(data.get("imageUrl")));
        vo.setLive2d(data.get("live2d"));
        vo.setSuggestedAppearance(data.get("suggestedAppearance"));
        vo.setTier(stringVal(data.get("tier")));
        vo.setPointsCost(intVal(data.get("pointsCost")));
        vo.setBalanceAfter(intVal(data.get("balanceAfter")));
        vo.setBillingMode(stringVal(data.get("billingMode")));
        vo.setUsageStats(toUsageStatsVO(data.get("usageStats")));
        vo.setModelCode(stringVal(data.get("modelCode")));
        Object est = data.get("estimated");
        if (est instanceof Boolean b) {
            vo.setEstimated(b);
        } else if (est != null) {
            vo.setEstimated(Boolean.parseBoolean(String.valueOf(est)));
        }
        return vo;
    }

    private static AiUsageStatsVO toUsageStatsVO(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        AiUsageStatsVO vo = new AiUsageStatsVO();
        vo.setModelCode(stringVal(m.get("modelCode")));
        vo.setInputTokens(intVal(m.get("inputTokens")));
        vo.setOutputTokens(intVal(m.get("outputTokens")));
        vo.setImageCount(intVal(m.get("imageCount")));
        vo.setLatencyMs(intVal(m.get("latencyMs")));
        Object estimated = m.get("estimated");
        if (estimated instanceof Boolean b) {
            vo.setEstimated(b);
        } else if (estimated != null) {
            vo.setEstimated(Boolean.parseBoolean(String.valueOf(estimated)));
        }
        vo.setBillingMode(stringVal(m.get("billingMode")));
        vo.setPointsCost(intVal(m.get("pointsCost")));
        return vo;
    }

    private static String stringVal(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static Integer intVal(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
