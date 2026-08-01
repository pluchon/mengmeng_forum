package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.common.constant.ForumRedisKeys;
import org.pluchon.forum.common.enums.HotArticleTrendDirection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 热帖榜 ZSet 蓝绿切换读写：重建写入 inactive slot，再原子切换 active 指针，避免 delete 全 key 的空榜窗口。
 */
@Component
public class HotArticleRedisOps {

    private static final String POINTER_A = "a";
    private static final String POINTER_B = "b";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String resolveActiveKey() {
        String pointer = stringRedisTemplate.opsForValue().get(ForumRedisKeys.HOT_ARTICLES_ACTIVE);
        if (POINTER_B.equals(pointer)) {
            return ForumRedisKeys.HOT_ARTICLES_SLOT_B;
        }
        return ForumRedisKeys.HOT_ARTICLES_SLOT_A;
    }

    public void incrementScore(Long articleId, double delta) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        stringRedisTemplate.opsForZSet().incrementScore(
                resolveActiveKey(), String.valueOf(articleId), delta);
    }

    public void setScore(Long articleId, double score) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(resolveActiveKey(), String.valueOf(articleId), score);
    }

    public void remove(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(resolveActiveKey(), String.valueOf(articleId));
    }

    public Set<String> reverseRange(long start, long end) {
        return stringRedisTemplate.opsForZSet().reverseRange(resolveActiveKey(), start, end);
    }

    public void rebuildBlueGreen(Map<String, Double> memberScores) {
        String inactiveKey = resolveInactiveKey();
        stringRedisTemplate.delete(inactiveKey);
        if (memberScores != null && !memberScores.isEmpty()) {
            for (Map.Entry<String, Double> entry : memberScores.entrySet()) {
                stringRedisTemplate.opsForZSet().add(inactiveKey, entry.getKey(), entry.getValue());
            }
        }
        String newPointer = ForumRedisKeys.HOT_ARTICLES_SLOT_A.equals(inactiveKey) ? POINTER_A : POINTER_B;
        stringRedisTemplate.opsForValue().set(ForumRedisKeys.HOT_ARTICLES_ACTIVE, newPointer);
    }

    public boolean isDailyTrendInitialized() {
        return "1".equals(stringRedisTemplate.opsForValue()
                .get(ForumRedisKeys.HOT_ARTICLES_TREND_INITIALIZED));
    }

    public Map<Long, String> readDailyMetricBaselines(List<Long> articleIds) {
        return readStringHash(ForumRedisKeys.HOT_ARTICLES_METRIC_BASELINE, articleIds);
    }

    public Map<Long, Double> readPreviousPeriodScores(List<Long> articleIds) {
        Map<Long, String> values = readStringHash(ForumRedisKeys.HOT_ARTICLES_PERIOD_SCORE, articleIds);
        Map<Long, Double> scores = new HashMap<>();
        for (Map.Entry<Long, String> entry : values.entrySet()) {
            try {
                scores.put(entry.getKey(), Double.parseDouble(entry.getValue()));
            } catch (NumberFormatException ignored) {
                // 损坏快照按首次统计处理
            }
        }
        return scores;
    }

    public Map<Long, HotArticleTrendDirection> readTrendDirections(List<Long> articleIds) {
        Map<Long, String> values = readStringHash(ForumRedisKeys.HOT_ARTICLES_TREND, articleIds);
        Map<Long, HotArticleTrendDirection> directions = new HashMap<>();
        for (Map.Entry<Long, String> entry : values.entrySet()) {
            try {
                directions.put(entry.getKey(), HotArticleTrendDirection.valueOf(entry.getValue()));
            } catch (IllegalArgumentException ignored) {
                directions.put(entry.getKey(), HotArticleTrendDirection.STABLE);
            }
        }
        return directions;
    }

    public void replaceDailyTrendSnapshots(Map<Long, String> metricBaselines,
                                           Map<Long, Double> periodScores,
                                           Map<Long, HotArticleTrendDirection> directions) {
        replaceStringHash(ForumRedisKeys.HOT_ARTICLES_METRIC_BASELINE, stringifyMap(metricBaselines));
        Map<String, String> scoreValues = new HashMap<>();
        for (Map.Entry<Long, Double> entry : periodScores.entrySet()) {
            scoreValues.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        replaceStringHash(ForumRedisKeys.HOT_ARTICLES_PERIOD_SCORE, scoreValues);
        Map<String, String> trendValues = new HashMap<>();
        for (Map.Entry<Long, HotArticleTrendDirection> entry : directions.entrySet()) {
            trendValues.put(String.valueOf(entry.getKey()), entry.getValue().name());
        }
        replaceStringHash(ForumRedisKeys.HOT_ARTICLES_TREND, trendValues);
        stringRedisTemplate.opsForValue().set(ForumRedisKeys.HOT_ARTICLES_TREND_INITIALIZED, "1");
    }

    private Map<Long, String> readStringHash(String key, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<String> fields = articleIds.stream().map(String::valueOf).toList();
        List<Object> values = stringRedisTemplate.opsForHash().multiGet(key, new ArrayList<>(fields));
        Map<Long, String> result = new HashMap<>();
        for (int index = 0; index < fields.size(); index++) {
            Object value = values != null && index < values.size() ? values.get(index) : null;
            if (value != null) {
                result.put(articleIds.get(index), String.valueOf(value));
            }
        }
        return result;
    }

    private void replaceStringHash(String key, Map<String, String> values) {
        stringRedisTemplate.delete(key);
        if (values != null && !values.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(key, values);
        }
    }

    private Map<String, String> stringifyMap(Map<Long, String> values) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<Long, String> entry : values.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String resolveInactiveKey() {
        String pointer = stringRedisTemplate.opsForValue().get(ForumRedisKeys.HOT_ARTICLES_ACTIVE);
        if (POINTER_B.equals(pointer)) {
            return ForumRedisKeys.HOT_ARTICLES_SLOT_A;
        }
        return ForumRedisKeys.HOT_ARTICLES_SLOT_B;
    }
}
