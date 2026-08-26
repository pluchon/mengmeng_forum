package org.pluchon.forum.converter;

import org.pluchon.forum.entity.dto.AiModelUsageDTO;
import org.pluchon.forum.entity.dto.AiArticleTagCandidateDTO;
import org.pluchon.forum.entity.dto.RagArticleIndexDTO;
import org.pluchon.forum.entity.dto.RagEmojiIndexDTO;
import org.pluchon.forum.entity.dto.RagMusicIndexDTO;
import org.pluchon.forum.entity.dto.RagUserIndexDTO;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubCreatorInsightResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleCoverResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagRecommendResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubArticleTagSimilarityResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubImageResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.ai.AiHubPolishResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationFeatureResultVO;
import org.pluchon.forum.entity.vo.ai.AiRecommendationProfileResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.ai.AiArticleCoverResponseVO;
import org.pluchon.forum.entity.vo.ai.AiUsageStatsVO;
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;
import org.pluchon.forum.entity.vo.ai.RagArticleVectorHitVO;
import org.pluchon.forum.entity.vo.ai.RagUserVectorHitVO;
import org.springframework.util.StringUtils;

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
        vo.setUsageItems(parseUsageItems(data.get("usage")));
        return vo;
    }

    public static AiHubArticleCoverResultVO toArticleCoverResult(Map<String, Object> data) {
        AiHubArticleCoverResultVO vo = new AiHubArticleCoverResultVO();
        if (data == null) {
            return vo;
        }
        vo.setUrl(stringVal(data.get("url")));
        vo.setPrompt(stringVal(data.get("prompt")));
        vo.setModel(stringVal(data.get("model")));
        Object mcpUsed = data.get("mcpUsed");
        vo.setMcpUsed(mcpUsed instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(mcpUsed)));
        vo.setUsage(parseUsage(data.get("usage")));
        vo.setUsageItems(parseUsageItems(data.get("usage")));
        return vo;
    }

    public static AiHubArticleTagRecommendResultVO toArticleTagRecommendResult(Map<String, Object> data) {
        AiHubArticleTagRecommendResultVO vo = new AiHubArticleTagRecommendResultVO();
        if (data == null) {
            vo.setTagIds(List.of());
            vo.setUsageItems(List.of());
            return vo;
        }
        vo.setTagIds(longList(data.get("tagIds")));
        vo.setSummary(stringVal(data.get("summary")));
        Object deepUsed = data.get("deepUsed");
        vo.setDeepUsed(deepUsed instanceof Boolean b
                ? b : Boolean.parseBoolean(String.valueOf(deepUsed)));
        vo.setUsageItems(parseUsageItems(data.get("usage")));
        return vo;
    }

    public static AiHubMusicMatchResultVO toMusicMatchResult(Map<String, Object> data) {
        AiHubMusicMatchResultVO vo = new AiHubMusicMatchResultVO();
        if (data == null) {
            vo.setMusicKeys(List.of());
            vo.setMoods(List.of());
            vo.setUsageItems(List.of());
            return vo;
        }
        vo.setMusicKeys(stringList(data.get("musicKeys")));
        vo.setRationale(stringVal(data.get("rationale")));
        vo.setMoods(stringList(data.get("moods")));
        vo.setUsageItems(parseUsageItems(data.get("usage")));
        return vo;
    }

    public static List<Map<String, Object>> musicCandidatesToMaps(
            List<org.pluchon.forum.entity.dto.AiMusicCandidateDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (org.pluchon.forum.entity.dto.AiMusicCandidateDTO candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getMusicKey())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("musicKey", candidate.getMusicKey());
            row.put("name", candidate.getName());
            if (StringUtils.hasText(candidate.getTitle())) {
                row.put("title", candidate.getTitle());
            }
            if (StringUtils.hasText(candidate.getArtist())) {
                row.put("artist", candidate.getArtist());
            }
            if (StringUtils.hasText(candidate.getAlbum())) {
                row.put("album", candidate.getAlbum());
            }
            result.add(row);
        }
        return result;
    }

    public static AiHubArticleTagSimilarityResultVO toArticleTagSimilarityResult(Map<String, Object> data) {
        AiHubArticleTagSimilarityResultVO vo = new AiHubArticleTagSimilarityResultVO();
        if (data == null) {
            return vo;
        }
        vo.setSimilarTagId(longVal(data.get("similarTagId")));
        vo.setReason(stringVal(data.get("reason")));
        return vo;
    }

    public static List<Map<String, Object>> articleTagCandidatesToMaps(
            List<AiArticleTagCandidateDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiArticleTagCandidateDTO candidate : candidates) {
            if (candidate == null || candidate.getId() == null || !StringUtils.hasText(candidate.getName())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", candidate.getId());
            item.put("name", candidate.getName().trim());
            result.add(item);
        }
        return result;
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
        vo.setPreferenceQuery(stringVal(data.get("preferenceQuery")));
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

    public static AiHubCreatorInsightResultVO toCreatorInsightResult(Map<String, Object> data) {
        AiHubCreatorInsightResultVO vo = new AiHubCreatorInsightResultVO();
        if (data == null) {
            return vo;
        }
        vo.setHeadline(stringVal(data.get("headline")));
        vo.setOverview(stringVal(data.get("overview")));
        vo.setHighlight(stringVal(data.get("highlight")));
        Object highlights = data.get("highlights");
        if (highlights instanceof List<?> values) {
            vo.setHighlights(values.stream().map(AiHubConverter::stringVal)
                    .filter(value -> value != null && !value.isBlank()).limit(3).toList());
        }
        return vo;
    }

    public static AiArticleCoverResponseVO toArticleCoverResponse(
            AiHubArticleCoverResultVO hub,
            String storedUrl,
            Map<String, Object> billing) {
        AiArticleCoverResponseVO vo = new AiArticleCoverResponseVO();
        if (hub != null) {
            vo.setPrompt(hub.getPrompt());
            vo.setModel(hub.getModel());
            vo.setMcpUsed(hub.getMcpUsed());
            vo.setUsage(hub.getUsage());
        }
        vo.setUrl(storedUrl);
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

    private static void applyBilling(AiArticleCoverResponseVO vo, Map<String, Object> billing) {
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
        map.put("category", dto.getCategory());
        map.put("coverUrl", dto.getCoverUrl());
        return map;
    }

    public static Map<String, Object> ragMusicIndexToMap(RagMusicIndexDTO dto) {
        Map<String, Object> map = new HashMap<>();
        if (dto == null) {
            return map;
        }
        map.put("musicKey", dto.getMusicKey());
        map.put("title", dto.getTitle());
        map.put("artist", dto.getArtist());
        map.put("genre", dto.getGenre());
        map.put("moodTags", dto.getMoodTags());
        map.put("aiProfile", dto.getAiProfile());
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
        dto.setStage(stringVal(um.get("stage")));
        Object mc = um.get("model_code");
        if (mc == null) {
            mc = um.get("model");
        }
        if (mc != null) {
            dto.setModelCode(String.valueOf(mc));
        }
        dto.setInputTokens(intVal(um.get("input_tokens")));
        dto.setOutputTokens(intVal(um.get("output_tokens")));
        Integer images = intVal(um.get("image_count"));
        if (images == null) {
            images = intVal(um.get("images"));
        }
        dto.setImageCount(images);
        Object estimated = um.get("estimated");
        if (estimated != null) {
            dto.setEstimated(estimated instanceof Boolean b
                    ? b : Boolean.parseBoolean(String.valueOf(estimated)));
        }
        dto.setLatencyMs(intVal(um.get("latency_ms")));
        return dto;
    }

    private static List<AiModelUsageDTO> parseUsageItems(Object raw) {
        if (!(raw instanceof Map<?, ?> usageMap)) {
            return List.of();
        }
        Object totals = usageMap.get("model_totals");
        List<?> list;
        if (totals instanceof List<?> modelTotals) {
            list = modelTotals;
        } else if (usageMap.get("items") instanceof List<?> callItems) {
            list = callItems;
        } else {
            AiModelUsageDTO single = parseUsage(raw);
            return single == null ? List.of() : List.of(single);
        }
        List<AiModelUsageDTO> result = new ArrayList<>();
        for (Object item : list) {
            AiModelUsageDTO usage = parseUsage(item);
            if (usage != null && StringUtils.hasText(usage.getModelCode())) {
                result.add(usage);
            }
        }
        return result;
    }

    public static List<AiModelUsageDTO> toUsageItems(Object raw) {
        return parseUsageItems(raw);
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

    private static List<Long> longList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            Long value = longVal(item);
            if (value != null && value > 0 && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
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
