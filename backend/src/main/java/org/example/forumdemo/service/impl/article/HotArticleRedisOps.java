package org.example.forumdemo.service.impl.article;

import org.example.forumdemo.common.constant.ForumRedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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

    private String resolveInactiveKey() {
        String pointer = stringRedisTemplate.opsForValue().get(ForumRedisKeys.HOT_ARTICLES_ACTIVE);
        if (POINTER_B.equals(pointer)) {
            return ForumRedisKeys.HOT_ARTICLES_SLOT_A;
        }
        return ForumRedisKeys.HOT_ARTICLES_SLOT_B;
    }
}
