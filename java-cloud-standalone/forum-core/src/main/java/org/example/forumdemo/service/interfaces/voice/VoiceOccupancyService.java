package org.example.forumdemo.service.interfaces.voice;

import java.time.Duration;
import java.util.Collection;

// 语音会话占用服务，保证同一用户同一时间只在一个语音会话中
public interface VoiceOccupancyService {

    void assertAvailable(Long userId, String sessionId);

    void bind(Long userId, String sessionId, Duration ttl);

    void bindAll(Collection<Long> userIds, String sessionId, Duration ttl);

    void release(Long userId, String sessionId);

    void releaseAll(Collection<Long> userIds, String sessionId);
}
