package org.example.forumdemo.service.impl.voice;

import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.service.interfaces.voice.VoiceOccupancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

@Service
// Redis 语音占用实现
public class VoiceOccupancyServiceImpl implements VoiceOccupancyService {

    // 用户语音占用 Key 前缀
    private static final String USER_SESSION_KEY_PREFIX = "voice:occupancy:user:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void assertAvailable(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String current = stringRedisTemplate.opsForValue().get(userSessionKey(userId));
        if (StringUtils.hasText(current) && !Objects.equals(current, sessionId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "请先退出当前语音聊天"));
        }
    }

    @Override
    public void bind(Long userId, String sessionId, Duration ttl) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(userSessionKey(userId), sessionId, ttl);
    }

    @Override
    public void bindAll(Collection<Long> userIds, String sessionId, Duration ttl) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            bind(userId, sessionId, ttl);
        }
    }

    @Override
    public void release(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        String key = userSessionKey(userId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (Objects.equals(current, sessionId)) {
            stringRedisTemplate.delete(key);
        }
    }

    @Override
    public void releaseAll(Collection<Long> userIds, String sessionId) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            release(userId, sessionId);
        }
    }

    private String userSessionKey(Long userId) {
        return USER_SESSION_KEY_PREFIX + userId;
    }
}
