package org.pluchon.forum.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.entity.db.UserRecommendationSetting;
import org.pluchon.forum.entity.vo.recommendation.UserRecommendationSettingVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// 推荐设置转换器
public final class RecommendationSettingConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_INTEREST_BOARDS = 5;

    private RecommendationSettingConverter() {
    }

    public static UserRecommendationSettingVO toVO(UserRecommendationSetting source) {
        UserRecommendationSettingVO target = new UserRecommendationSettingVO();
        target.setPersonalizedEnabled(source == null || Byte.valueOf((byte) 1).equals(source.getPersonalizedEnabled()));
        target.setInterestBoardIds(parseInterestBoardIds(source == null ? null : source.getInterestBoardIds()));
        return target;
    }

    public static List<Long> parseInterestBoardIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<Long> parsed = OBJECT_MAPPER.readValue(raw, new TypeReference<>() { });
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<Long> unique = new LinkedHashSet<>();
            for (Long boardId : parsed) {
                if (boardId != null && boardId > 0) {
                    unique.add(boardId);
                }
                if (unique.size() >= MAX_INTEREST_BOARDS) {
                    break;
                }
            }
            return new ArrayList<>(unique);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String serializeInterestBoardIds(List<Long> boardIds) {
        List<Long> normalized = normalizeInterestBoardIds(boardIds);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Long> normalizeInterestBoardIds(List<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long boardId : boardIds) {
            if (boardId != null && boardId > 0) {
                unique.add(boardId);
            }
            if (unique.size() >= MAX_INTEREST_BOARDS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }
}
