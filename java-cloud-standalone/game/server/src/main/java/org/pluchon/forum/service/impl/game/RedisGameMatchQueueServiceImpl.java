package org.pluchon.forum.service.impl.game;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.GameRedisKeys;
import org.pluchon.forum.entity.bo.game.GameMatchBucket;
import org.pluchon.forum.entity.bo.game.GameMatchPair;
import org.pluchon.forum.service.interfaces.game.GameMatchQueueService;
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

    private static final String ENQUEUE_SCRIPT = """
            local occupiedGame = redis.call('GET', KEYS[1])
            if occupiedGame and occupiedGame ~= ARGV[1] then
                return -1
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                if not occupiedGame then
                    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[5])
                else
                    redis.call('EXPIRE', KEYS[1], ARGV[5])
                end
                redis.call('EXPIRE', KEYS[2], ARGV[5])
                return 0
            end
            if not occupiedGame then
                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[5])
            else
                redis.call('EXPIRE', KEYS[1], ARGV[5])
            end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[5])
            redis.call('ZADD', KEYS[3], ARGV[4], ARGV[3])
            return 1
            """;

    private static final String DEQUEUE_SCRIPT = """
            local removed = redis.call('DEL', KEYS[2])
            for index = 3, #KEYS do
                redis.call('ZREM', KEYS[index], ARGV[2])
            end
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
            end
            return removed
            """;

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
            if redis.call('GET', ARGV[2] .. picked[1]) == ARGV[3] then
                redis.call('DEL', ARGV[2] .. picked[1])
            end
            if redis.call('GET', ARGV[2] .. picked[2]) == ARGV[3] then
                redis.call('DEL', ARGV[2] .. picked[2])
            end
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
                    if redis.call('GET', ARGV[3] .. userId) == ARGV[4] then
                        redis.call('DEL', ARGV[3] .. userId)
                    end
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
        String occupancyKey = GameRedisKeys.matchOccupancy(userId);
        String markerKey = GameRedisKeys.matchUser(gameCode, userId);
        String queueKey = GameRedisKeys.matchQueue(gameCode, bucket.getBucketCode());
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(ENQUEUE_SCRIPT, Long.class);
            Long result = stringRedisTemplate.execute(
                    script,
                    List.of(occupancyKey, markerKey, queueKey),
                    gameCode,
                    bucket.getBucketCode(),
                    String.valueOf(userId),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(MATCH_MARK_TTL.toSeconds())
            );
            return Long.valueOf(1L).equals(result);
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
        try {
            List<String> keys = new java.util.ArrayList<>();
            keys.add(GameRedisKeys.matchOccupancy(userId));
            keys.add(GameRedisKeys.matchUser(gameCode, userId));
            for (String bucket : BUCKETS) {
                keys.add(GameRedisKeys.matchQueue(gameCode, bucket));
            }
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEQUEUE_SCRIPT, Long.class);
            Long removed = stringRedisTemplate.execute(
                    script,
                    keys,
                    gameCode,
                    String.valueOf(userId)
            );
            return removed != null && removed > 0;
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
    public String matchingGameCode(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return stringRedisTemplate.opsForValue().get(GameRedisKeys.matchOccupancy(userId));
        } catch (Exception e) {
            log.debug("读取 Redis 用户匹配占用失败 userId={}, error={}", userId, e.getMessage());
            return null;
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
                    GameRedisKeys.matchUserPrefix(gameCode),
                    GameRedisKeys.matchOccupancyPrefix(),
                    gameCode
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
                    String.valueOf(cutoff),
                    GameRedisKeys.matchOccupancyPrefix(),
                    gameCode
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
