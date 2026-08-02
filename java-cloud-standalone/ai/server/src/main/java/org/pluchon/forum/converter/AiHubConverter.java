package org.pluchon.forum.converter;

import org.pluchon.forum.entity.dto.ai.AiModelUsageDTO;
import org.pluchon.forum.entity.dto.ai.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.ai.RagEmojiIndexDTO;
import org.pluchon.forum.entity.dto.ai.RagUserIndexDTO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.ai.AiUsageStatsVO;
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// AI Hub 响应与 RAG 载荷转换
public final class AiHubConverter {

    private AiHubConverter() {
    }

    public static AiHubPolishResultVO toPolishResult(Map<String, Object> data) {
        AiHubPolishResultVO vo = new AiHubPolishResultVO();
        if (data == null) {
            return vo;
        }
        String text = stringVal(data.get("text"));
        if (text == null) {
            text = stringVal(data.get("content"));
        }
        vo.setText(text);
        vo.setModel(stringVal(data.get("model")));
        vo.setProvider(stringVal(data.get("provider")));
        vo.setUsage(parseUsage(data.get("usage")));
        return vo;
    }

    public static AiHubCoverHintsResultVO toCoverHintsResult(Map<String, Object> data) {
        AiHubCoverHintsResultVO vo = new AiHubCoverHintsResultVO();
        if (data == null) {
            return vo;
        }
        vo.setContent(stringVal(data.get("content")));
        vo.setHints(stringList(data.get("hints")));
        vo.setThemes(stringList(data.get("themes")));
        vo.setSummary(stringVal(data.get("summary")));
        return vo;
    }

    public static AiHubImageResultVO toImageResult(Map<String, Object> data) {
        AiHubImageResultVO vo = new AiHubImageResultVO();
        if (data == null) {
            return vo;
        }
        String url = stringVal(data.get("url"));
        if (url == null && data.get("payload") instanceof Map<?, ?> payload) {
            url = stringVal(payload.get("url"));
        }
        vo.setUrl(url);
        vo.setModel(stringVal(data.get("model")));
        vo.setUsage(parseUsage(data.get("usage")));
        return vo;
    }

    public static AiRecommendationFeatureResultVO toRecommendationFeatureResult(Map<String, Object> data) {
        AiRecommendationFeatureResultVO vo = new AiRecommendationFeatureResultVO();
        if (data == null) {
            return vo;
        }
        vo.setArticleId(longVal(data.get("articleId")));
        vo.setFeatureVersion(stringVal(data.get("featureVersion")));
        vo.setTopics(mapList(data.get("topics")));
        vo.setSummary(stringVal(data.get("summary")));
        vo.setContentFingerprint(stringVal(data.get("contentFingerprint")));
        vo.setGeneratedBy(stringVal(data.get("generatedBy")));
        return vo;
    }

    public static AiRecommendationProfileResultVO toRecommendationProfileResult(Map<String, Object> data) {
        AiRecommendationProfileResultVO vo = new AiRecommendationProfileResultVO();
        if (data == null) {
            return vo;
        }
        vo.setFeatureVersion(stringVal(data.get("featureVersion")));
        vo.setTopics(mapList(data.get("topics")));
        vo.setAvoidTopics(mapList(data.get("avoidTopics")));
        vo.setSummary(stringVal(data.get("summary")));
        vo.setGeneratedBy(stringVal(data.get("generatedBy")));
        return vo;
    }

    public static AiPolishResponseVO toPolishResponse(AiHubPolishResultVO hub, Map<String, Object> billing) {
        AiPolishResponseVO vo = new AiPolishResponseVO();
        if (hub != null) {
            vo.setText(hub.getText());
            vo.setModel(hub.getModel());
            vo.setProvider(hub.getProvider());
            vo.setUsage(hub.getUsage());
        }
        applyBilling(vo, billing);
        return vo;
    }

    public static AiImageResponseVO toImageResponse(AiHubImageResultVO hub, String modelCode, String sessionId,
                                                    String storedUrl, Map<String, Object> billing) {
        AiImageResponseVO vo = new AiImageResponseVO();
        if (hub != null) {
            vo.setModel(hub.getModel());
            vo.setUsage(hub.getUsage());
        }
        vo.setUrl(storedUrl);
        vo.setModelCode(modelCode);
        vo.setSessionId(sessionId);
        applyBilling(vo, billing);
        return vo;
    }

    private static void applyBilling(AiPolishResponseVO vo, Map<String, Object> billing) {
        if (vo == null || billing == null) {
            return;
        }
        vo.setPointsCost(intVal(billing.get("pointsCost")));
        vo.setBalanceAfter(intVal(billing.get("balanceAfter")));
        vo.setBillingMode(stringVal(billing.get("billingMode")));
        vo.setUsageStats(toUsageStatsVO(billing.get("usageStats")));
    }

    private static void applyBilling(AiImageResponseVO vo, Map<String, Object> billing) {
        if (vo == null || billing == null) {
            return;
        }
        vo.setPointsCost(intVal(billing.get("pointsCost")));
        vo.setBalanceAfter(intVal(billing.get("balanceAfter")));
        vo.setBillingMode(stringVal(billing.get("billingMode")));
        vo.setUsageStats(toUsageStatsVO(billing.get("usageStats")));
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

    public static Map<String, Object> ragArticleIndexToMap(RagArticleIndexDTO dto) {
        Map<String, Object> map = new HashMap<>();
        if (dto == null) {
            return map;
        }
        map.put("articleId", dto.getArticleId());
        map.put("title", dto.getTitle());
        map.put("content", dto.getContent());
        map.put("mediaType", dto.getMediaType());
        map.put("videoUrl", dto.getVideoUrl());
        map.put("coverUrl", dto.getCoverUrl());
        map.put("summary", dto.getSummary());
        map.put("authorNickname", dto.getAuthorNickname());
        map.put("tagNames", dto.getTagNames());
        return map;
    }

    public static Map<String, Object> ragUserIndexToMap(RagUserIndexDTO dto) {
        Map<String, Object> map = new HashMap<>(4);
        if (dto == null) {
            return map;
        }
        map.put("userId", dto.getUserId());
        map.put("nickname", dto.getNickname());
        map.put("username", dto.getUsername());
        map.put("remark", dto.getRemark());
        return map;
    }

    public static Map<String, Object> ragEmojiIndexToMap(RagEmojiIndexDTO dto) {
        Map<String, Object> map = new HashMap<>();
        if (dto == null) {
            return map;
        }
        map.put("shopId", dto.getShopId());
        map.put("name", dto.getName());
        map.put("description", dto.getDescription());
        map.put("coverUrl", dto.getCoverUrl());
        return map;
    }

    public static List<RagArticleVectorHitVO> toArticleVectorHits(List<?> results) {
        List<RagArticleVectorHitVO> ranked = new ArrayList<>();
        if (results == null) {
            return ranked;
        }
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = longVal(row.get("articleId"));
            if (id == null) {
                continue;
            }
            RagArticleVectorHitVO hit = new RagArticleVectorHitVO();
            hit.setArticleId(id);
            hit.setScore(doubleVal(row.get("score")));
            ranked.add(hit);
        }
        return ranked;
    }

    public static List<RagUserVectorHitVO> toUserVectorHits(List<?> results) {
        List<RagUserVectorHitVO> ranked = new ArrayList<>();
        if (results == null) {
            return ranked;
        }
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = longVal(row.get("userId"));
            if (id == null) {
                id = longVal(row.get("user_id"));
            }
            if (id == null) {
                continue;
            }
            RagUserVectorHitVO hit = new RagUserVectorHitVO();
            hit.setUserId(id);
            hit.setScore(doubleVal(row.get("score")));
            ranked.add(hit);
        }
        return ranked;
    }

    private static AiModelUsageDTO parseUsage(Object raw) {
        if (!(raw instanceof Map<?, ?> um)) {
            return null;
        }
        AiModelUsageDTO dto = new AiModelUsageDTO();
        Object mc = um.get("model_code");
        if (mc == null) {
            mc = um.get("model");
        }
        if (mc != null) {
            dto.setModelCode(String.valueOf(mc));
        }
        dto.setInputTokens(intVal(um.get("input_tokens")));
        dto.setOutputTokens(intVal(um.get("output_tokens")));
        dto.setImageCount(intVal(um.get("image_count")));
        return dto;
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

    private static Long longVal(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double doubleVal(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> mapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalized = new HashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            result.add(normalized);
        }
        return result;
    }
}
