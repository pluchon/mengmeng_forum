package org.example.forumdemo.service.impl.game;

import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.GameRedisKeys;
import org.example.forumdemo.entity.bo.game.GameMatchBucket;
import org.example.forumdemo.entity.bo.game.GameMatchPair;
import org.example.forumdemo.service.interfaces.game.GameMatchQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

// Redis 游戏匹配队列，使用用户标记防重复入队，并用 Lua 保证取人和出队原子完成
@Slf4j
@Service
public class RedisGameMatchQueueServiceImpl implements GameMatchQueueService {

    private static final Duration MATCH_MARK_TTL = Duration.ofSeconds(180);

    private static final List<String> BUCKETS = List.of(
            GameConstants.MATCH_BUCKET_BRONZE,
            GameConstants.MATCH_BUCKET_SILVER,
            GameConstants.MATCH_BUCKET_GOLD,
            GameConstants.MATCH_BUCKET_MASTER
    );

    private static final String POLL_PAIR_SCRIPT = """
            local picked = {}
            local candidates = redis.call('ZRANGE', KEYS[1], 0, 9)
            for _, userId in ipairs(candidates) do
                local markerKey = ARGV[1] .. userId
                if redis.call('EXISTS', markerKey) == 1 then
                    table.insert(picked, userId)
                    if #picked == 2 then
                        break
                    end
                else
                    redis.call('ZREM', KEYS[1], userId)
                end
            end
            if #picked < 2 then
                return {}
            end
            redis.call('ZREM', KEYS[1], picked[1], picked[2])
            redis.call('DEL', ARGV[1] .. picked[1], ARGV[1] .. picked[2])
            return picked
            """;

    private static final String POLL_AI_SCRIPT = """
            local candidates = redis.call('ZRANGE', KEYS[1], 0, 9, 'WITHSCORES')
            local index = 1
            while index <= #candidates do
                local userId = candidates[index]
                local queuedAt = tonumber(candidates[index + 1])
                local markerKey = ARGV[1] .. userId
                if redis.call('EXISTS', markerKey) == 0 then
                    redis.call('ZREM', KEYS[1], userId)
                elseif queuedAt <= tonumber(ARGV[2]) then
                    redis.call('ZREM', KEYS[1], userId)
                    redis.call('DEL', markerKey)
                    return userId
                end
                index = index + 2
            end
            return nil
            """;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean enqueue(String gameCode, Long userId, GameMatchBucket bucket) {
        if (gameCode == null || gameCode.isBlank() || userId == null || bucket == null) {
            return false;
        }
        String markerKey = GameRedisKeys.matchUser(gameCode, userId);
        String queueKey = GameRedisKeys.matchQueue(gameCode, bucket.getBucketCode());
        try {
            Boolean marked = stringRedisTemplate.opsForValue().setIfAbsent(
                    markerKey,
                    bucket.getBucketCode(),
                    MATCH_MARK_TTL
            );
            if (!Boolean.TRUE.equals(marked)) {
                return false;
            }
            Boolean added = stringRedisTemplate.opsForZSet().add(
                    queueKey,
                    String.valueOf(userId),
                    System.currentTimeMillis()
            );
            if (!Boolean.TRUE.equals(added)) {
                stringRedisTemplate.delete(markerKey);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Redis 游戏匹配入队失败 gameCode={}, userId={}, error={}",
                    gameCode,
                    userId,
                    e.getMessage());
            return false;
        }
    }

    @Override
    public boolean dequeue(String gameCode, Long userId) {
        if (gameCode == null || gameCode.isBlank() || userId == null) {
            return false;
        }
        String member = String.valueOf(userId);
        try {
            Boolean hadMarker = stringRedisTemplate.delete(GameRedisKeys.matchUser(gameCode, userId));
            for (String bucket : BUCKETS) {
                stringRedisTemplate.opsForZSet().remove(GameRedisKeys.matchQueue(gameCode, bucket), member);
            }
            return Boolean.TRUE.equals(hadMarker);
        } catch (Exception e) {
            log.debug("Redis 游戏匹配出队失败 gameCode={}, userId={}, error={}",
                    gameCode,
                    userId,
                    e.getMessage());
            return false;
        }
    }

    @Override
    public boolean contains(String gameCode, Long userId) {
        if (gameCode == null || gameCode.isBlank() || userId == null) {
            return false;
        }
        try {
            Boolean exists = stringRedisTemplate.hasKey(GameRedisKeys.matchUser(gameCode, userId));
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("读取 Redis 游戏匹配状态失败 gameCode={}, userId={}, error={}",
                    gameCode,
                    userId,
                    e.getMessage());
            return false;
        }
    }

    @Override
    public GameMatchPair pollPair(String gameCode, String bucketCode) {
        if (gameCode == null || gameCode.isBlank() || bucketCode == null || bucketCode.isBlank()) {
            return null;
        }
        try {
            DefaultRedisScript<List> script = new DefaultRedisScript<>(POLL_PAIR_SCRIPT, List.class);
            List<?> rows = stringRedisTemplate.execute(
                    script,
                    List.of(GameRedisKeys.matchQueue(gameCode, bucketCode)),
                    GameRedisKeys.matchUserPrefix(gameCode)
            );
            if (rows == null || rows.size() < 2) {
                return null;
            }
            Long userIdA = parseUserId(rows.get(0));
            Long userIdB = parseUserId(rows.get(1));
            if (userIdA == null || userIdB == null || userIdA.equals(userIdB)) {
                return null;
            }
            return new GameMatchPair(userIdA, userIdB);
        } catch (Exception e) {
            log.debug("Redis 游戏匹配取双人失败 gameCode={}, bucket={}, error={}",
                    gameCode,
                    bucketCode,
                    e.getMessage());
            return null;
        }
    }

    @Override
    public Long pollAiCandidate(String gameCode, String bucketCode, long waitTimeoutMs) {
        if (gameCode == null || gameCode.isBlank() || bucketCode == null || bucketCode.isBlank()) {
            return null;
        }
        long cutoff = System.currentTimeMillis() - Math.max(0, waitTimeoutMs);
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(POLL_AI_SCRIPT, String.class);
            String userId = stringRedisTemplate.execute(
                    script,
                    List.of(GameRedisKeys.matchQueue(gameCode, bucketCode)),
                    GameRedisKeys.matchUserPrefix(gameCode),
                    String.valueOf(cutoff)
            );
            return parseUserId(userId);
        } catch (Exception e) {
            log.debug("Redis 游戏匹配取 AI 候选失败 gameCode={}, bucket={}, error={}",
                    gameCode,
                    bucketCode,
                    e.getMessage());
            return null;
        }
    }

    private Long parseUserId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
