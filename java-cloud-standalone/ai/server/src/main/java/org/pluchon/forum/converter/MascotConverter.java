package org.pluchon.forum.converter;

import org.pluchon.forum.entity.vo.ai.AiUsageStatsVO;
import org.pluchon.forum.entity.vo.MascotChatResponseVO;
import org.pluchon.forum.entity.vo.CompanionImageGalleryItemVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 看板娘响应转换
public final class MascotConverter {

    private MascotConverter() {
    }

    public static MascotChatResponseVO toChatResponse(Map<String, Object> data) {
        MascotChatResponseVO vo = new MascotChatResponseVO();
        if (data == null) {
            return vo;
        }
        vo.setSessionId(stringVal(data.get("sessionId")));
        vo.setReply(stringVal(data.get("reply")));
        vo.setImageUrl(stringVal(data.get("imageUrl")));
        vo.setImageError(stringVal(data.get("imageError")));
        vo.setLive2d(data.get("live2d"));
        vo.setSuggestedAppearance(data.get("suggestedAppearance"));
        vo.setTier(stringVal(data.get("tier")));
        vo.setPointsCost(intVal(data.get("pointsCost")));
        vo.setBalanceAfter(intVal(data.get("balanceAfter")));
        vo.setBillingMode(stringVal(data.get("billingMode")));
        vo.setUsageStats(toUsageStatsVO(data.get("usageStats")));
        vo.setModelCode(stringVal(data.get("modelCode")));
        Object relatedSearchOffer = data.get("relatedSearchOffer");
        if (relatedSearchOffer instanceof Boolean b) {
            vo.setRelatedSearchOffer(b);
        } else if (relatedSearchOffer != null) {
            vo.setRelatedSearchOffer(Boolean.parseBoolean(String.valueOf(relatedSearchOffer)));
        }
        vo.setRelatedSearchQuery(stringVal(data.get("relatedSearchQuery")));
        vo.setSearchImageGallery(toImageGallery(data.get("searchImageGallery")));
        Object est = data.get("estimated");
        if (est instanceof Boolean b) {
            vo.setEstimated(b);
        } else if (est != null) {
            vo.setEstimated(Boolean.parseBoolean(String.valueOf(est)));
        }
        return vo;
    }

    private static List<CompanionImageGalleryItemVO> toImageGallery(Object raw) {
        if (!(raw instanceof List<?> rows)) {
            return List.of();
        }
        List<CompanionImageGalleryItemVO> out = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) {
                continue;
            }
            Object rawUrl = item.get("url");
            String url = rawUrl == null ? "" : String.valueOf(rawUrl).trim();
            if (!url.startsWith("https://") || url.length() > 2048) {
                continue;
            }
            CompanionImageGalleryItemVO galleryItem = new CompanionImageGalleryItemVO();
            galleryItem.setUrl(url);
            galleryItem.setTitle(trimTitle(item.get("title")));
            galleryItem.setSource(stringVal(item.get("source")));
            out.add(galleryItem);
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
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

    private static String trimTitle(Object raw) {
        String title = stringVal(raw);
        if (title == null) {
            return "";
        }
        title = title.trim();
        return title.substring(0, Math.min(10, title.length()));
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
