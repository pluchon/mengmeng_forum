package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.common.constant.ForumRedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 音乐热榜 ZSet 蓝绿读写：重建写 inactive，再切 active，避免空榜窗口
@Component
public class HotMusicRedisOps {

    private static final String POINTER_A = "a";
    private static final String POINTER_B = "b";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String resolveActiveKey() {
        String pointer = stringRedisTemplate.opsForValue().get(ForumRedisKeys.HOT_MUSIC_ACTIVE);
        if (POINTER_B.equals(pointer)) {
            return ForumRedisKeys.HOT_MUSIC_SLOT_B;
        }
        return ForumRedisKeys.HOT_MUSIC_SLOT_A;
    }

    public void setScore(String musicKey, double score) {
        if (!StringUtils.hasText(musicKey)) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(resolveActiveKey(), musicKey.trim(), score);
    }

    public void remove(String musicKey) {
        if (!StringUtils.hasText(musicKey)) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(resolveActiveKey(), musicKey.trim());
    }

    public List<String> reverseRange(long start, long end) {
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRange(resolveActiveKey(), start, end);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }

    public long size() {
        Long size = stringRedisTemplate.opsForZSet().zCard(resolveActiveKey());
        return size == null ? 0L : size;
    }

    public void rebuildBlueGreen(Map<String, Double> memberScores) {
        if (memberScores == null || memberScores.isEmpty()) {
            return;
        }
        String inactiveKey = resolveInactiveKey();
        stringRedisTemplate.delete(inactiveKey);
        for (Map.Entry<String, Double> entry : memberScores.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            stringRedisTemplate.opsForZSet().add(inactiveKey, entry.getKey().trim(), entry.getValue());
        }
        String newPointer = ForumRedisKeys.HOT_MUSIC_SLOT_A.equals(inactiveKey) ? POINTER_A : POINTER_B;
        stringRedisTemplate.opsForValue().set(ForumRedisKeys.HOT_MUSIC_ACTIVE, newPointer);
    }

    private String resolveInactiveKey() {
        String pointer = stringRedisTemplate.opsForValue().get(ForumRedisKeys.HOT_MUSIC_ACTIVE);
        if (POINTER_B.equals(pointer)) {
            return ForumRedisKeys.HOT_MUSIC_SLOT_A;
        }
        return ForumRedisKeys.HOT_MUSIC_SLOT_B;
    }
}
