package org.pluchon.forum.service.impl.checkin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.GrowthExperienceSourceType;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.CursorUtils;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.CheckinLog;
import org.pluchon.forum.entity.db.CheckinRule;
import org.pluchon.forum.entity.db.CheckinStreakReward;
import org.pluchon.forum.entity.db.UserCheckinInfo;
import org.pluchon.forum.entity.vo.checkin.CheckinResultResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleDayResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinStatusResponse;
import org.pluchon.forum.entity.vo.common.CursorPageResult;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.CheckinLogMapper;
import org.pluchon.forum.mapper.CheckinRuleMapper;
import org.pluchon.forum.mapper.CheckinStreakRewardMapper;
import org.pluchon.forum.mapper.UserCheckinInfoMapper;
import org.pluchon.forum.service.interfaces.checkin.CheckinService;
import org.pluchon.forum.service.interfaces.growth.GrowthExperienceService;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 签到模块实现.
 * 静态规则 (checkin_rule / checkin_streak_reward) 启动时加载到内存, 不入 Redis.
 */
@Service
@Slf4j
public class CheckinServiceImpl implements CheckinService {

    private static final int CHECKIN_GROWTH_EXPERIENCE = 5;

    //明确时区
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 兜底默认月份 (checkin_rule.month=0) */
    private static final int DEFAULT_RULE_MONTH = 0;

    @Autowired
    private CheckinRuleMapper checkinRuleMapper;

    @Autowired
    private CheckinStreakRewardMapper checkinStreakRewardMapper;

    @Autowired
    private CheckinLogMapper checkinLogMapper;

    @Autowired
    private UserCheckinInfoMapper userCheckinInfoMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private GrowthExperienceService growthExperienceService;

    /** 月份 -> (日 -> 积分). key=0 表示通用兜底规则; 服务启动时一次性装入, 供 doCheckin 热路径零延迟查 */
    private volatile Map<Integer, Map<Integer, Integer>> ruleCache = Collections.emptyMap();

    /** 连续签到奖励, 按 streakDays 升序; 精确匹配; 同上, 仅 service 内部使用 */
    private volatile List<CheckinStreakReward> rewardCache = Collections.emptyList();

    /** 静态规则是否已成功加载; 启动时 DB 不可用则延迟到首次业务调用再试 */
    private volatile boolean cacheInitialized = false;

    @PostConstruct
    public void initCache() {
        try {
            refreshCacheInternal();
            cacheInitialized = true;
            log.info("签到规则缓存加载完成: {} 个月份规则, {} 个连签奖励档", ruleCache.size(), rewardCache.size());
        } catch (Exception e) {
            log.warn("启动时加载签到规则缓存失败, 将在首次签到相关请求时重试. "
                            + "请确认 MySQL(127.0.0.1:33306/forum_db) 已启动且 checkin_rule、checkin_streak_reward 表已初始化. 原因: {}",
                    e.getMessage());
        }
    }

    private void ensureCacheLoaded() {
        if (cacheInitialized) {
            return;
        }
        synchronized (this) {
            if (cacheInitialized) {
                return;
            }
            refreshCacheInternal();
            cacheInitialized = true;
            log.info("签到规则缓存延迟加载完成: {} 个月份规则, {} 个连签奖励档", ruleCache.size(), rewardCache.size());
        }
    }

    private void refreshCacheInternal() {
        reloadRuleCache();
        reloadRewardCache();
    }

    private void reloadRuleCache() {
        List<CheckinRule> rules = checkinRuleMapper.selectList(new LambdaQueryWrapper<CheckinRule>().ne(CheckinRule::getDeleteState, 1));
        Map<Integer, Map<Integer, Integer>> grouped = new HashMap<>();
        for (CheckinRule r : rules) {
            int m = r.getMonth() == null ? DEFAULT_RULE_MONTH : r.getMonth().intValue();
            int d = r.getDayNumber() == null ? 0 : r.getDayNumber().intValue();
            grouped.computeIfAbsent(m, k -> new HashMap<>()).put(d, r.getPoints());
        }
        this.ruleCache = grouped;
    }

    private void reloadRewardCache() {
        this.rewardCache = checkinStreakRewardMapper.selectList(new LambdaQueryWrapper<CheckinStreakReward>()
                .ne(CheckinStreakReward::getDeleteState, 1).orderByAsc(CheckinStreakReward::getStreakDays));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResultResponse doCheckin(Long userId) {
        ensureCacheLoaded();
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        Date todayDate = toDate(today);
        int pointsToday = resolvePoints(today);
        UserCheckinInfo info = userCheckinInfoMapper.selectOne(new LambdaQueryWrapper<UserCheckinInfo>()
                .eq(UserCheckinInfo::getUserId, userId).ne(UserCheckinInfo::getDeleteState, 1));
        int newStreak = computeNewStreak(info, today);
        int bonusPoints = 0;
        String bonusDescription = null;
        CheckinStreakReward reward = findReward(newStreak);
        if (reward != null) {
            bonusPoints = reward.getBonusPoints() == null ? 0 : reward.getBonusPoints();
            bonusDescription = reward.getDescription();
        }
        // 写入流水
        CheckinLog logRow = new CheckinLog();
        logRow.setUserId(userId);
        logRow.setCheckinDate(todayDate);
        logRow.setPoints(pointsToday);
        logRow.setBonusPoints(bonusPoints);
        logRow.setStreakDays(newStreak);
        try {
            checkinLogMapper.insert(logRow);
        } catch (DuplicateKeyException e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CHECKIN_DUPLICATE));
        }
        // 写入 / 更新汇总
        int addPoints = pointsToday + bonusPoints;
        UserCheckinInfo afterCheckin = upsertUserCheckinInfo(info, userId, newStreak, addPoints, todayDate);
        // 入账积分钱包: 基础 + 连签奖励分两条流水, 来源不同, 便于前端 ECharts 分色展示
        pointsService.addPoints(userId, pointsToday, Constant.POINTS_SOURCE_CHECKIN_BASIC,
                logRow.getId(), "签到 +" + pointsToday, "checkin_basic:" + userId + ":" + today);
        if (bonusPoints > 0) {
            pointsService.addPoints(userId, bonusPoints, Constant.POINTS_SOURCE_CHECKIN_BONUS,
                    logRow.getId(), "连续 " + newStreak + " 天奖励 +" + bonusPoints,
                    "checkin_bonus:" + userId + ":" + today);
        }
        growthExperienceService.grantExperience(
                userId,
                GrowthExperienceSourceType.CHECKIN,
                logRow.getId(),
                CHECKIN_GROWTH_EXPERIENCE,
                "每日签到");
        // 主动失效 status 缓存, 下一次 /info 调用会重新从 DB 拉取并回填
        invalidateStatusCache(userId);
        return new CheckinResultResponse(pointsToday, bonusPoints, bonusDescription, afterCheckin.getStreakDays(),
                afterCheckin.getTotalDays(), afterCheckin.getTotalPoints(), afterCheckin.getLastCheckin()
        );
    }

    @Override
    public CheckinStatusResponse getStatus(Long userId) {
        ensureCacheLoaded();
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String cacheKey = Constant.REDIS_KEY_CHECKIN_STATUS + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, CheckinStatusResponse.class);
            } catch (Exception e) {
                log.error("反序列化签到状态缓存失败, userId: {}", userId, e);
            }
        }
        CheckinStatusResponse response = buildStatusFromDb(userId);
        try {
            long ttl = Math.min(Constant.REDIS_TTL_CHECKIN_STATUS, secondsUntilNextMidnight());
            if (ttl > 0) {
                stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response),
                        ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("序列化签到状态写入缓存失败, userId: {}", userId, e);
        }
        return response;
    }

    /** 从 DB 直读并组装 CheckinStatusResponse, 不经过缓存 */
    private CheckinStatusResponse buildStatusFromDb(Long userId) {
        UserCheckinInfo info = userCheckinInfoMapper.selectOne(new LambdaQueryWrapper<UserCheckinInfo>()
                .eq(UserCheckinInfo::getUserId, userId).ne(UserCheckinInfo::getDeleteState, 1));
        LocalDate today = LocalDate.now(SHANGHAI);
        int streakDays = 0;
        int totalDays = 0;
        int totalPoints = 0;
        Date lastCheckin = null;
        boolean todaySigned = false;
        if (info != null) {
            streakDays = info.getStreakDays() == null ? 0 : info.getStreakDays();
            totalDays = info.getTotalDays() == null ? 0 : info.getTotalDays();
            totalPoints = info.getTotalPoints() == null ? 0 : info.getTotalPoints();
            lastCheckin = info.getLastCheckin();
            LocalDate lastLocal = toLocalDate(lastCheckin);
            todaySigned = lastLocal != null && lastLocal.equals(today);
        }
        Integer nextThreshold = null;
        Integer nextThresholdBonus = null;
        Integer nextThresholdLeft = null;
        for (CheckinStreakReward r : rewardCache) {
            if (r.getStreakDays() != null && r.getStreakDays() > streakDays) {
                nextThreshold = r.getStreakDays();
                nextThresholdBonus = r.getBonusPoints();
                nextThresholdLeft = r.getStreakDays() - streakDays;
                break;
            }
        }
        return new CheckinStatusResponse(streakDays, totalDays, totalPoints, lastCheckin, todaySigned,
                nextThreshold, nextThresholdBonus, nextThresholdLeft);
    }

    @Override
    public PageResult<CheckinLog> getLogWithPage(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<CheckinLog> page = new Page<>(validPageNum, validPageSize);
        Page<CheckinLog> result = checkinLogMapper.selectPage(page, new LambdaQueryWrapper<CheckinLog>().eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1).orderByDesc(CheckinLog::getCheckinDate).orderByDesc(CheckinLog::getId));
        return new PageResult<>(result.getRecords(), result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
    }

    @Override
    public CheckinRuleMonthResponse getRule(Integer month) {
        int target = (month == null || month < 1 || month > 12) ? LocalDate.now(SHANGHAI).getMonthValue() : month;
        String cacheKey = Constant.REDIS_KEY_CHECKIN_RULE + target;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, CheckinRuleMonthResponse.class);
            } catch (Exception e) {
                log.error("反序列化签到规则缓存失败, month: {}", target, e);
            }
        }
        CheckinRuleMonthResponse response = buildRuleFromMemory(target);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response),
                    Constant.REDIS_TTL_CHECKIN_RULE, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("序列化签到规则写入缓存失败, month: {}", target, e);
        }
        return response;
    }

    /** 基于内存的 ruleCache 拼装月度规则响应, 不经过 Redis */
    private CheckinRuleMonthResponse buildRuleFromMemory(int target) {
        Map<Integer, Integer> monthRule = ruleCache.get(target);
        if (monthRule == null || monthRule.isEmpty()) {
            monthRule = ruleCache.getOrDefault(DEFAULT_RULE_MONTH, Collections.emptyMap());
        }
        List<CheckinRuleDayResponse> days = new ArrayList<>(monthRule.size());
        monthRule.forEach((day, points) -> days.add(new CheckinRuleDayResponse(day, points)));
        days.sort(Comparator.comparingInt(CheckinRuleDayResponse::getDayNumber));
        return new CheckinRuleMonthResponse(target, days);
    }

    @Override
    public CursorPageResult<CheckinLog> getLogWithCursor(Long userId, String cursor, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int size = PageUtils.getValidPageSize(pageSize);
        LambdaQueryWrapper<CheckinLog> wrapper = new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1);
        if (cursor != null && !cursor.isBlank()) {
            CursorUtils.CursorToken token = CursorUtils.decode(cursor);
            Date cursorTime = new Date(token.timeMillis());
            wrapper.and(w -> w.lt(CheckinLog::getCheckinDate, cursorTime)
                    .or(w2 -> w2.eq(CheckinLog::getCheckinDate, cursorTime)
                            .lt(CheckinLog::getId, token.id())));
        }
        wrapper.orderByDesc(CheckinLog::getCheckinDate).orderByDesc(CheckinLog::getId);
        Page<CheckinLog> page = new Page<>(1, size + 1, false);
        List<CheckinLog> rows = checkinLogMapper.selectPage(page, wrapper).getRecords();
        boolean hasNext = rows.size() > size;
        if (hasNext) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        String nextCursor = null;
        if (hasNext && !rows.isEmpty()) {
            CheckinLog last = rows.get(rows.size() - 1);
            nextCursor = CursorUtils.encode(last.getCheckinDate(), last.getId());
        }
        return new CursorPageResult<>(rows, nextCursor, hasNext, size);
    }

    /** 根据上次签到日期推算签到后的连续天数 */
    private int computeNewStreak(UserCheckinInfo info, LocalDate today) {
        if (info == null) {
            return 1;
        }
        LocalDate last = toLocalDate(info.getLastCheckin());
        if (last == null) {
            return 1;
        }
        if (last.equals(today)) {
            // 防御性兜底, 正常情况下会被 checkin_log 唯一键先拦住
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CHECKIN_DUPLICATE));
        }
        int oldStreak = info.getStreakDays() == null ? 0 : info.getStreakDays();
        return today.minusDays(1).equals(last) ? oldStreak + 1 : 1;
    }

    /** 命中且仅命中 streakDays 精确等值的连签奖励 (不做循环) */
    private CheckinStreakReward findReward(int streakDays) {
        for (CheckinStreakReward r : rewardCache) {
            if (r.getStreakDays() != null && r.getStreakDays() == streakDays) {
                return r;
            }
        }
        return null;
    }

    /** 根据 today 命中规则缓存, 找不到具体月份则回退 month=0; 仍找不到给最低保底 10 分 */
    private int resolvePoints(LocalDate today) {
        int m = today.getMonthValue();
        int d = today.getDayOfMonth();
        Map<Integer, Integer> monthRule = ruleCache.get(m);
        if (monthRule != null && monthRule.containsKey(d)) {
            return monthRule.get(d);
        }
        Map<Integer, Integer> defaultRule = ruleCache.get(DEFAULT_RULE_MONTH);
        if (defaultRule != null && defaultRule.containsKey(d)) {
            return defaultRule.get(d);
        }
        log.warn("checkin_rule 未命中 month={} day={}, 走最低保底积分", m, d);
        return 10;
    }

    /** 将签到事件落到 user_checkin_info: 不存在则 INSERT, 存在则 UPDATE */
    private UserCheckinInfo upsertUserCheckinInfo(UserCheckinInfo existing, Long userId, int newStreak, int addPoints, Date todayDate) {
        if (existing == null) {
            UserCheckinInfo created = new UserCheckinInfo();
            created.setUserId(userId);
            created.setTotalDays(1);
            created.setStreakDays(newStreak);
            created.setTotalPoints(addPoints);
            created.setLastCheckin(todayDate);
            try {
                userCheckinInfoMapper.insert(created);
                return created;
            } catch (DuplicateKeyException e) {
                // 极端并发首签场景: 另一并发请求刚刚 INSERT, 退化为 UPDATE
                log.warn("user_checkin_info 并发首签 INSERT 冲突, 退化为 UPDATE: userId={}", userId);
                UserCheckinInfo refreshed = userCheckinInfoMapper.selectOne(new LambdaQueryWrapper<UserCheckinInfo>()
                        .eq(UserCheckinInfo::getUserId, userId).ne(UserCheckinInfo::getDeleteState, 1));
                if (refreshed == null) {
                    throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
                }
                return updateExistingInfo(refreshed, newStreak, addPoints, todayDate);
            }
        }
        return updateExistingInfo(existing, newStreak, addPoints, todayDate);
    }

    private UserCheckinInfo updateExistingInfo(UserCheckinInfo existing, int newStreak, int addPoints, Date todayDate) {
        int newTotalDays = (existing.getTotalDays() == null ? 0 : existing.getTotalDays()) + 1;
        int newTotalPoints = (existing.getTotalPoints() == null ? 0 : existing.getTotalPoints()) + addPoints;
        userCheckinInfoMapper.update(null, new LambdaUpdateWrapper<UserCheckinInfo>().eq(UserCheckinInfo::getUserId, existing.getUserId())
                .set(UserCheckinInfo::getTotalDays, newTotalDays).set(UserCheckinInfo::getStreakDays, newStreak)
                .set(UserCheckinInfo::getTotalPoints, newTotalPoints).set(UserCheckinInfo::getLastCheckin, todayDate));
        existing.setTotalDays(newTotalDays);
        existing.setStreakDays(newStreak);
        existing.setTotalPoints(newTotalPoints);
        existing.setLastCheckin(todayDate);
        return existing;
    }

    private static Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(SHANGHAI).toInstant());
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(SHANGHAI).toLocalDate();
    }

    /**
     * 距离下一个 Asia/Shanghai 自然日 0 点的秒数. status 缓存 TTL 取
     * {@code min(REDIS_TTL_CHECKIN_STATUS, secondsUntilNextMidnight())},
     * 保证 todaySigned 字段不会跨天误命中.
     */
    private static long secondsUntilNextMidnight() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(SHANGHAI);
        return Math.max(1L, Duration.between(now, nextMidnight).getSeconds());
    }

    private void invalidateStatusCache(Long userId) {
        try {
            stringRedisTemplate.delete(Constant.REDIS_KEY_CHECKIN_STATUS + userId);
        } catch (Exception e) {
            log.error("失效签到状态缓存失败, userId: {}", userId, e);
        }
    }
}
