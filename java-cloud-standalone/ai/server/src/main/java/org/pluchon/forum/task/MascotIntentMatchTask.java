package org.pluchon.forum.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.ForumTimeZone;
import org.pluchon.forum.entity.db.ForumMascotIntent;
import org.pluchon.forum.entity.db.ForumMascotIntentMatch;
import org.pluchon.forum.mapper.ForumMascotIntentMapper;
import org.pluchon.forum.mapper.ForumMascotIntentMatchMapper;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.mascot.MascotIntentMatchService;
import org.pluchon.forum.cloud.feign.AiSystemMessageInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 看板娘牵线：把池子里的意愿两两配一遍。
 *
 * <p>离线跑，不在任何用户的等待路径上。频率刻意压得很低——牵线是「偶尔有惊喜」，
 * 不是「每天都来烦你」。宁可慢一天配上，也不要为了快而降低判定标准。
 */
@Slf4j
@Component
public class MascotIntentMatchTask {

    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_EXPIRED = "EXPIRED";

    @Autowired
    private ForumMascotIntentMapper intentMapper;

    @Autowired
    private ForumMascotIntentMatchMapper matchMapper;

    @Autowired
    private MascotIntentMatchService matchService;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private AiSystemMessageInternalFeignClient systemMessageClient;

    /** 单次任务最多送多少对去判定，给成本封顶 */
    @Value("${forum.mascot.intent-match-batch:100}")
    private int matchBatch;

    /** 候选凑到预算的几倍：多给一些让向量有得挑，判定数仍由 matchBatch 封顶 */
    @Value("${forum.mascot.intent-match-candidate-multiplier:5}")
    private int candidateMultiplier;

    @Value("${forum.mascot.intent-match-enabled:true}")
    private boolean matchEnabled;

    /** 每天凌晨跑一次。人少的时候大多数轮次什么都不会发生，这很正常。 */
    @Scheduled(cron = "0 20 4 * * *", zone = ForumTimeZone.ID)
    public void run() {
        if (!matchEnabled) {
            return;
        }
        try {
            expireStale();
            matchOnce();
        } catch (Exception e) {
            log.error("看板娘牵线任务失败", e);
        }
    }

    /** 过期的意愿先清掉：拿着半年前的需求去牵线只会让人莫名其妙。 */
    private void expireStale() {
        int n = intentMapper.update(null, new LambdaUpdateWrapper<ForumMascotIntent>()
                .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                .le(ForumMascotIntent::getExpireAt, new Date())
                .set(ForumMascotIntent::getState, STATE_EXPIRED)
                .set(ForumMascotIntent::getUpdateTime, new Date()));
        if (n > 0) {
            log.info("[牵线] 过期意愿 {} 条", n);
        }
    }

    private void matchOnce() {
        List<ForumMascotIntent> pool = intentMapper.selectList(
                new LambdaQueryWrapper<ForumMascotIntent>()
                        .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                        .gt(ForumMascotIntent::getExpireAt, new Date())
                        .orderByAsc(ForumMascotIntent::getId));
        if (pool.size() < 2) {
            return;
        }
        List<ForumMascotIntent> seeks = new ArrayList<>();
        List<ForumMascotIntent> offers = new ArrayList<>();
        for (ForumMascotIntent row : pool) {
            if ("offer".equals(row.getIntentKind())) {
                offers.add(row);
            } else {
                seeks.add(row);
            }
        }
        if (seeks.isEmpty() || offers.isEmpty()) {
            log.info("[牵线] 池中 seek={} offer={}，本轮无可配对", seeks.size(), offers.size());
            return;
        }

        Set<String> tried = loadTriedPairs();
        int budget = Math.max(1, matchBatch);
        // 多凑一些候选交给向量挑，真正判定的仍然不超过 budget
        int candidateLimit = Math.min(600, budget * Math.max(1, candidateMultiplier));
        List<Map<String, Object>> pairs = new ArrayList<>();
        Map<String, ForumMascotIntent[]> byKey = new HashMap<>();
        outer:
        for (ForumMascotIntent seek : seeks) {
            for (ForumMascotIntent offer : offers) {
                // 不给自己牵线；同一对意愿只判一次
                if (seek.getUserId().equals(offer.getUserId())) {
                    continue;
                }
                String key = seek.getId() + "-" + offer.getId();
                if (tried.contains(key)) {
                    continue;
                }
                Map<String, Object> pair = new HashMap<>();
                pair.put("key", key);
                pair.put("a", seek.getIntentText());
                pair.put("b", offer.getIntentText());
                pairs.add(pair);
                byKey.put(key, new ForumMascotIntent[]{seek, offer});
                if (pairs.size() >= candidateLimit) {
                    break outer;
                }
            }
        }
        if (pairs.isEmpty()) {
            return;
        }

        List<Map<String, Object>> results = aiHubService.matchMascotIntents(pairs, budget);
        int connected = 0;
        Set<Long> usedIntents = new HashSet<>();
        for (Map<String, Object> row : results) {
            String key = String.valueOf(row.get("key"));
            ForumMascotIntent[] both = byKey.get(key);
            if (!Boolean.TRUE.equals(row.get("match"))) {
                // 判过就要留痕，否则明天原样再判一遍，永远轮不到第 batch+1 对
                if (both != null) {
                    try {
                        matchService.recordNotMatched(both[0], both[1]);
                    } catch (Exception e) {
                        log.warn("[牵线] 记录不匹配失败 key={}", key, e);
                    }
                }
                continue;
            }
            String reason = row.get("reason") == null ? "" : String.valueOf(row.get("reason")).trim();
            if (both != null && reason.isEmpty()) {
                // 说配上却说不出交集，按不配处理，同样要留痕
                try {
                    matchService.recordNotMatched(both[0], both[1]);
                } catch (Exception e) {
                    log.warn("[牵线] 记录不匹配失败 key={}", key, e);
                }
            }
            if (both == null || reason.isEmpty()) {
                continue;
            }
            // 同一轮里一条意愿只牵一次线，别把一个人同时推给三个人
            if (!usedIntents.add(both[0].getId()) || !usedIntents.add(both[1].getId())) {
                continue;
            }
            try {
                ForumMascotIntentMatch match = matchService.createMatch(both[0], both[1], reason);
                notifyBoth(match, reason);
                connected++;
            } catch (Exception e) {
                log.warn("[牵线] 建立匹配失败 key={}", key, e);
            }
        }
        log.info("[牵线] 候选 {} 对，实判 {} 对，牵成 {} 对", pairs.size(), results.size(), connected);
    }

    /**
     * 给双方各发一条邀约。
     *
     * <p>通知里只有 reason，**不含任何身份信息**——在双方都点头之前，
     * 谁都不知道对面是谁。
     */
    private void notifyBoth(ForumMascotIntentMatch match, String reason) {
        String title = "有人和你想到一块了";
        String content = "看板娘发现一位站友和你有交集：" + reason + "。要不要认识一下？";
        sendNotice(match.getUserAId(), title, content, match.getId());
        sendNotice(match.getUserBId(), title, content, match.getId());
    }

    private void sendNotice(Long userId, String title, String content, Long matchId) {
        try {
            // payload 里带上 matchId，前端点通知就能直接打开这次牵线
            systemMessageClient.createMessage(userId, matchService.noticeType(), title, content,
                    matchId, "{\"kind\":\"intent_match\",\"matchId\":" + matchId + "}");
        } catch (Exception e) {
            log.warn("[牵线] 邀约通知投递失败 userId={} matchId={}", userId, matchId, e);
        }
    }

    private Set<String> loadTriedPairs() {
        Set<String> tried = new HashSet<>();
        for (ForumMascotIntentMatch row : matchMapper.selectList(new LambdaQueryWrapper<>())) {
            tried.add(row.getIntentAId() + "-" + row.getIntentBId());
            tried.add(row.getIntentBId() + "-" + row.getIntentAId());
        }
        return tried;
    }
}
