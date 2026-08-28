package org.pluchon.forum.service.impl.checkin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.CursorUtils;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.CheckinGrantLog;
import org.pluchon.forum.entity.db.CheckinLog;
import org.pluchon.forum.entity.db.CheckinRule;
import org.pluchon.forum.entity.db.CheckinStreakReward;
import org.pluchon.forum.entity.db.CheckinSurprisePool;
import org.pluchon.forum.entity.db.UserCheckinInfo;
import org.pluchon.forum.entity.vo.checkin.CheckinMonthDayVO;
import org.pluchon.forum.entity.vo.checkin.CheckinMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinResultResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleDayResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinRuleMonthResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinStatusResponse;
import org.pluchon.forum.entity.vo.checkin.CheckinLogVO;
import org.pluchon.forum.entity.vo.checkin.CheckinStreakRewardItemVO;
import org.pluchon.forum.entity.vo.checkin.CheckinWeekStatVO;
import org.pluchon.forum.entity.vo.common.CursorPageResult;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.CheckinGrantLogMapper;
import org.pluchon.forum.mapper.CheckinLogMapper;
import org.pluchon.forum.mapper.CheckinRuleMapper;
import org.pluchon.forum.mapper.CheckinStreakRewardMapper;
import org.pluchon.forum.mapper.CheckinSurprisePoolMapper;
import org.pluchon.forum.mapper.UserCheckinInfoMapper;
import org.pluchon.forum.service.impl.starlight.StarlightServiceImpl;
import org.pluchon.forum.service.interfaces.checkin.CheckinService;
import org.pluchon.forum.service.interfaces.lottery.LotteryVoucherService;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.starlight.StarlightService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// 签到模块实现：日常签到 / 补签 / 连签混合奖 / 惊喜奖
@Service
@Slf4j
public class CheckinServiceImpl implements CheckinService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Taipei");
    private static final int DEFAULT_RULE_MONTH = 0;
    private static final int MAKEUP_LOOKBACK_DAYS = 30;

    private static final String GRANT_STREAK = "STREAK";
    private static final String GRANT_SURPRISE = "SURPRISE";
    private static final String REWARD_POINTS = "POINTS";
    private static final String REWARD_STARLIGHT = "STARLIGHT";
    private static final String REWARD_MAKEUP_CARD = "MAKEUP_CARD";
    private static final String REWARD_VIP_DAYS = "VIP_DAYS";
    private static final String REWARD_LOTTERY_VOUCHER = "LOTTERY_VOUCHER";

    @Autowired
    private CheckinRuleMapper checkinRuleMapper;

    @Autowired
    private CheckinStreakRewardMapper checkinStreakRewardMapper;

    @Autowired
    private CheckinLogMapper checkinLogMapper;

    @Autowired
    private UserCheckinInfoMapper userCheckinInfoMapper;

    @Autowired
    private CheckinGrantLogMapper checkinGrantLogMapper;

    @Autowired
    private CheckinSurprisePoolMapper checkinSurprisePoolMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private StarlightService starlightService;

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private LotteryVoucherService lotteryVoucherService;

    // 月份 > 日 > 日规则
    private volatile Map<Integer, Map<Integer, DayRule>> ruleCache = Collections.emptyMap();

    private volatile List<CheckinStreakReward> rewardCache = Collections.emptyList();

    private volatile List<CheckinSurprisePool> surprisePoolCache = Collections.emptyList();

    private volatile boolean cacheInitialized = false;

    private record DayRule(int points, boolean surprise) {
    }

    private record SurpriseRoll(String type, int value, String label) {
    }

    @PostConstruct
    public void initCache() {
        try {
            refreshCacheInternal();
            cacheInitialized = true;
            log.info("签到规则缓存加载完成: {} 个月份规则, {} 个连签奖励档, {} 个惊喜奖池项",
                    ruleCache.size(), rewardCache.size(), surprisePoolCache.size());
        } catch (Exception e) {
            log.warn("启动时加载签到规则缓存失败, 将在首次签到相关请求时重试. 原因: {}", e.getMessage());
        }
    }

    private void ensureCacheLoaded() {
        if (cacheInitialized && !ruleCache.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (cacheInitialized && !ruleCache.isEmpty()) {
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
        reloadSurprisePoolCache();
    }

    private void reloadRuleCache() {
        List<CheckinRule> rules = checkinRuleMapper.selectList(
                new LambdaQueryWrapper<CheckinRule>().ne(CheckinRule::getDeleteState, 1));
        Map<Integer, Map<Integer, DayRule>> grouped = new HashMap<>();
        for (CheckinRule r : rules) {
            int m = r.getMonth() == null ? DEFAULT_RULE_MONTH : r.getMonth().intValue();
            int d = r.getDayNumber() == null ? 0 : r.getDayNumber().intValue();
            int points = r.getPoints() == null ? 0 : r.getPoints();
            boolean surprise = r.getIsSurprise() != null && r.getIsSurprise() == 1;
            grouped.computeIfAbsent(m, k -> new HashMap<>()).put(d, new DayRule(points, surprise));
        }
        this.ruleCache = grouped;
    }

    private void reloadRewardCache() {
        this.rewardCache = checkinStreakRewardMapper.selectList(
                new LambdaQueryWrapper<CheckinStreakReward>()
                        .ne(CheckinStreakReward::getDeleteState, 1)
                        .orderByAsc(CheckinStreakReward::getStreakDays));
    }

    private void reloadSurprisePoolCache() {
        this.surprisePoolCache = checkinSurprisePoolMapper.selectList(
                new LambdaQueryWrapper<CheckinSurprisePool>()
                        .ne(CheckinSurprisePool::getDeleteState, 1)
                        .orderByAsc(CheckinSurprisePool::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResultResponse doCheckin(Long userId) {
        ensureCacheLoaded();
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        return performCheckin(userId, today, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResultResponse makeupCheckin(Long userId) {
        ensureCacheLoaded();
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 日期由服务端决定：离今天最近的漏签日，禁止客户端自选刷惊喜/连签档
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate target = findNearestMissedDate(userId, today);
        if (target == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CHECKIN_MAKEUP_DATE_INVALID));
        }
        if (!consumeMakeupCard(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CHECKIN_MAKEUP_CARD_NOT_ENOUGH));
        }
        return performCheckin(userId, target, true);
    }

    // 在近 30 天内，从昨天往前找第一个未签日
    private LocalDate findNearestMissedDate(Long userId, LocalDate today) {
        LocalDate earliest = today.minusDays(MAKEUP_LOOKBACK_DAYS);
        List<CheckinLog> recent = checkinLogMapper.selectList(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1)
                .ge(CheckinLog::getCheckinDate, toDate(earliest))
                .lt(CheckinLog::getCheckinDate, toDate(today)));
        Set<LocalDate> signed = new HashSet<>();
        for (CheckinLog row : recent) {
            LocalDate d = toLocalDate(row.getCheckinDate());
            if (d != null) {
                signed.add(d);
            }
        }
        for (LocalDate cursor = today.minusDays(1); !cursor.isBefore(earliest); cursor = cursor.minusDays(1)) {
            if (!signed.contains(cursor)) {
                return cursor;
            }
        }
        return null;
    }

    private CheckinResultResponse performCheckin(Long userId, LocalDate checkinDay, boolean makeup) {
        Date checkinDate = toDate(checkinDay);
        int pointsToday = resolvePoints(checkinDay);
        UserCheckinInfo info = loadUserInfo(userId);
        int oldStreak = info == null || info.getStreakDays() == null ? 0 : info.getStreakDays();

        CheckinLog logRow = new CheckinLog();
        logRow.setUserId(userId);
        logRow.setCheckinDate(checkinDate);
        logRow.setPoints(pointsToday);
        logRow.setBonusPoints(0);
        logRow.setIsMakeup(makeup ? (byte) 1 : (byte) 0);
        try {
            checkinLogMapper.insert(logRow);
        } catch (DuplicateKeyException e) {
            if (makeup) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CHECKIN_MAKEUP_ALREADY_SIGNED));
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CHECKIN_DUPLICATE));
        }

        int newStreak = recalcStreakFromLogs(userId, LocalDate.now(SHANGHAI));
        logRow.setStreakDays(newStreak);
        checkinLogMapper.updateById(logRow);

        String bonusDescription = grantCrossedStreakRewards(userId, oldStreak, newStreak, logRow.getId());
        int bonusPointsSnapshot = 0;
        CheckinStreakReward exact = findReward(newStreak);
        if (exact != null && exact.getBonusPoints() != null) {
            bonusPointsSnapshot = exact.getBonusPoints();
        }
        logRow.setBonusPoints(bonusPointsSnapshot);
        checkinLogMapper.updateById(logRow);

        // 补签不发惊喜奖：防止故意漏惊喜日再补签刷奖；连签档仍按重算后的 streak 幂等补发
        SurpriseRoll surprise = null;
        if (!makeup && isSurpriseDay(checkinDay)) {
            surprise = tryGrantSurprise(userId, checkinDay, logRow.getId());
            if (surprise != null) {
                logRow.setSurpriseType(surprise.type());
                logRow.setSurpriseValue(surprise.value());
                logRow.setSurpriseLabel(surprise.label());
                checkinLogMapper.updateById(logRow);
            }
        }

        if (!makeup) {
            pointsService.addPoints(userId, pointsToday, Constant.POINTS_SOURCE_CHECKIN_BASIC,
                    logRow.getId(), "签到 +" + pointsToday, "checkin_basic:" + userId + ":" + checkinDay);
        } else {
            pointsService.addPoints(userId, pointsToday, Constant.POINTS_SOURCE_CHECKIN_BASIC,
                    logRow.getId(), "补签 +" + pointsToday, "checkin_makeup:" + userId + ":" + checkinDay);
        }

        UserCheckinInfo after = upsertUserCheckinInfoAfterEvent(info, userId, newStreak, pointsToday + bonusPointsSnapshot);
        invalidateStatusCache(userId);

        return buildResultResponse(after, pointsToday, bonusPointsSnapshot, bonusDescription, surprise, checkinDay);
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

    private CheckinStatusResponse buildStatusFromDb(Long userId) {
        UserCheckinInfo info = loadUserInfo(userId);
        LocalDate today = LocalDate.now(SHANGHAI);
        int streakDays = 0;
        int totalDays = 0;
        int totalPoints = 0;
        Date lastCheckin = null;
        boolean todaySigned = false;
        int makeupCardCount = 0;
        if (info != null) {
            streakDays = info.getStreakDays() == null ? 0 : info.getStreakDays();
            totalDays = info.getTotalDays() == null ? 0 : info.getTotalDays();
            totalPoints = info.getTotalPoints() == null ? 0 : info.getTotalPoints();
            lastCheckin = info.getLastCheckin();
            LocalDate lastLocal = toLocalDate(lastCheckin);
            todaySigned = lastLocal != null && lastLocal.equals(today);
            makeupCardCount = info.getMakeupCardCount() == null ? 0 : info.getMakeupCardCount();
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
        return new CheckinStatusResponse(
                streakDays,
                totalDays,
                totalPoints,
                lastCheckin,
                todaySigned,
                nextThreshold,
                nextThresholdBonus,
                nextThresholdLeft,
                makeupCardCount,
                buildStreakRewardItems(streakDays));
    }

    @Override
    public CheckinMonthResponse getMonth(Long userId, Integer year, Integer month) {
        ensureCacheLoaded();
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        int y = year == null ? today.getYear() : year;
        int m = month == null ? today.getMonthValue() : month;
        if (m < 1 || m > 12) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        YearMonth ym = YearMonth.of(y, m);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<CheckinLog> logs = checkinLogMapper.selectList(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ge(CheckinLog::getCheckinDate, toDate(start))
                .le(CheckinLog::getCheckinDate, toDate(end))
                .ne(CheckinLog::getDeleteState, 1));
        Map<LocalDate, CheckinLog> byDate = new HashMap<>();
        for (CheckinLog row : logs) {
            LocalDate d = toLocalDate(row.getCheckinDate());
            if (d != null) {
                byDate.put(d, row);
            }
        }

        List<CheckinMonthDayVO> days = new ArrayList<>();
        int[] weekBuckets = new int[6];
        int signedCount = 0;
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            DayRule rule = resolveDayRule(date);
            CheckinLog row = byDate.get(date);
            boolean signed = row != null;
            if (signed) {
                signedCount++;
                int weekIndex = ((day - 1) / 7);
                weekBuckets[weekIndex]++;
            }
            days.add(new CheckinMonthDayVO(
                    date.toString(),
                    day,
                    rule.points(),
                    signed,
                    row != null && row.getIsMakeup() != null && row.getIsMakeup() == 1,
                    rule.surprise(),
                    date.equals(today)));
        }

        List<CheckinWeekStatVO> weeklyStats = new ArrayList<>();
        int weeks = (ym.lengthOfMonth() + 6) / 7;
        for (int i = 0; i < weeks; i++) {
            weeklyStats.add(new CheckinWeekStatVO(i + 1, weekBuckets[i]));
        }
        return new CheckinMonthResponse(y, m, signedCount, days, weeklyStats);
    }

    @Override
    public PageResult<CheckinLogVO> getLogWithPage(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = Math.min(10, PageUtils.getValidPageSize(pageSize));
        Page<CheckinLog> page = new Page<>(validPageNum, validPageSize);
        Page<CheckinLog> result = checkinLogMapper.selectPage(page, new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1)
                .orderByDesc(CheckinLog::getCheckinDate)
                .orderByDesc(CheckinLog::getId));
        List<CheckinLogVO> records = result.getRecords().stream().map(this::toCheckinLogVO).toList();
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    private CheckinLogVO toCheckinLogVO(CheckinLog row) {
        CheckinLogVO vo = new CheckinLogVO();
        vo.setId(row.getId());
        vo.setCreateTime(row.getCreateTime());
        vo.setAttributionDate(row.getCheckinDate());
        vo.setCheckinType(row.getIsMakeup() != null && row.getIsMakeup() == 1 ? "补签" : "正常签到");
        vo.setPoints((row.getPoints() == null ? 0 : row.getPoints())
                + (row.getBonusPoints() == null ? 0 : row.getBonusPoints()));
        vo.setStreakDays(row.getStreakDays());
        vo.setSurpriseLabel(row.getSurpriseLabel());
        return vo;
    }

    @Override
    public CheckinRuleMonthResponse getRule(Integer month) {
        ensureCacheLoaded();
        int target = (month == null || month < 1 || month > 12)
                ? LocalDate.now(SHANGHAI).getMonthValue()
                : month;
        String cacheKey = Constant.REDIS_KEY_CHECKIN_RULE + target;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                CheckinRuleMonthResponse fromRedis = objectMapper.readValue(cached, CheckinRuleMonthResponse.class);
                if (fromRedis != null && fromRedis.getDays() != null && !fromRedis.getDays().isEmpty()) {
                    return fromRedis;
                }
            } catch (Exception e) {
                log.error("反序列化签到规则缓存失败, month: {}", target, e);
            }
        }
        CheckinRuleMonthResponse response = buildRuleFromMemory(target);
        if (response.getDays() != null && !response.getDays().isEmpty()) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response),
                        Constant.REDIS_TTL_CHECKIN_RULE, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("序列化签到规则写入缓存失败, month: {}", target, e);
            }
        }
        return response;
    }

    private CheckinRuleMonthResponse buildRuleFromMemory(int target) {
        Map<Integer, DayRule> monthRule = ruleCache.get(target);
        if (monthRule == null || monthRule.isEmpty()) {
            monthRule = ruleCache.getOrDefault(DEFAULT_RULE_MONTH, Collections.emptyMap());
        }
        List<CheckinRuleDayResponse> days = new ArrayList<>(monthRule.size());
        monthRule.forEach((day, rule) -> days.add(new CheckinRuleDayResponse(day, rule.points(), rule.surprise())));
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
            Date cursorTime;
            if (token != null) {
                cursorTime = new Date(token.timeMillis());
            } else {
                cursorTime = null;
            }
            wrapper.and(w -> w.lt(CheckinLog::getCheckinDate, cursorTime)
                    .or(w2 -> {
                        if (token != null) {
                            w2.eq(CheckinLog::getCheckinDate, cursorTime)
                                    .lt(CheckinLog::getId, token.id());
                        }
                    }));
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

    private String grantCrossedStreakRewards(Long userId, int oldStreak, int newStreak, Long logId) {
        String lastDesc = null;
        for (CheckinStreakReward reward : rewardCache) {
            if (reward.getStreakDays() == null) {
                continue;
            }
            int threshold = reward.getStreakDays();
            if (oldStreak < threshold && threshold <= newStreak) {
                if (tryBeginGrant(userId, GRANT_STREAK, "streak:" + userId + ":" + threshold,
                        reward.getRewardType(), threshold, reward.getTitle(), logId)) {
                    grantStreakBundle(userId, reward, logId);
                    lastDesc = reward.getDescription() != null ? reward.getDescription() : reward.getTitle();
                }
            }
        }
        return lastDesc;
    }

    private void grantStreakBundle(Long userId, CheckinStreakReward reward, Long logId) {
        int points = reward.getBonusPoints() == null ? 0 : reward.getBonusPoints();
        int starlight = reward.getStarlightAmount() == null ? 0 : reward.getStarlightAmount();
        int cards = reward.getMakeupCardAmount() == null ? 0 : reward.getMakeupCardAmount();
        int vipDays = reward.getVipDays() == null ? 0 : reward.getVipDays();
        if (points > 0) {
            pointsService.addPoints(userId, points, Constant.POINTS_SOURCE_CHECKIN_BONUS,
                    logId, "连签奖励 +" + points, "checkin_streak_pts:" + userId + ":" + reward.getStreakDays());
        }
        if (starlight > 0) {
            starlightService.credit(userId, starlight, StarlightServiceImpl.SOURCE_CHECKIN, logId,
                    "checkin_streak_sl:" + userId + ":" + reward.getStreakDays(),
                    "连签萌星辉 +" + starlight);
        }
        if (cards > 0) {
            addMakeupCards(userId, cards);
        }
        if (vipDays > 0) {
            vipSubscribeService.grantTrialVipDays(userId, vipDays, "CHECKIN_STREAK",
                    "CHECKIN_STREAK:" + userId + ":" + reward.getStreakDays());
        }
    }

    private SurpriseRoll tryGrantSurprise(Long userId, LocalDate day, Long logId) {
        String bizKey = "surprise:" + userId + ":" + day;
        CheckinSurprisePool picked = pickSurprise();
        if (picked == null) {
            return null;
        }
        String type = picked.getRewardType();
        int value = picked.getRewardValue() == null ? 0 : picked.getRewardValue();
        String label = picked.getLabel();
        if (!tryBeginGrant(userId, GRANT_SURPRISE, bizKey, type, value, label, logId)) {
            return null;
        }
        grantTypedReward(userId, type, value, logId, "checkin_surprise:" + userId + ":" + day, "惊喜奖励 " + label);
        return new SurpriseRoll(type, value, label);
    }

    private void grantTypedReward(Long userId, String type, int value, Long logId, String idempotencyKey, String remark) {
        if (value <= 0 || type == null) {
            return;
        }
        switch (type) {
            case REWARD_POINTS -> pointsService.addPoints(userId, value, Constant.POINTS_SOURCE_CHECKIN_SURPRISE,
                    logId, remark, idempotencyKey);
            case REWARD_STARLIGHT -> starlightService.credit(userId, value, StarlightServiceImpl.SOURCE_CHECKIN,
                    logId, idempotencyKey, remark);
            case REWARD_MAKEUP_CARD -> addMakeupCards(userId, value);
            case REWARD_VIP_DAYS -> vipSubscribeService.grantTrialVipDays(
                    userId, value, "CHECKIN_SURPRISE", idempotencyKey);
            case REWARD_LOTTERY_VOUCHER -> lotteryVoucherService.credit(
                    userId, value, logId, idempotencyKey, remark, Constant.LOTTERY_VOUCHER_SOURCE_CHECKIN);
            default -> log.warn("未知签到奖励类型: {}", type);
        }
    }

    private CheckinSurprisePool pickSurprise() {
        if (surprisePoolCache == null || surprisePoolCache.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (CheckinSurprisePool item : surprisePoolCache) {
            totalWeight += Math.max(0, item.getWeight() == null ? 0 : item.getWeight());
        }
        if (totalWeight <= 0) {
            return surprisePoolCache.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (CheckinSurprisePool item : surprisePoolCache) {
            cursor += Math.max(0, item.getWeight() == null ? 0 : item.getWeight());
            if (roll < cursor) {
                return item;
            }
        }
        return surprisePoolCache.get(surprisePoolCache.size() - 1);
    }

    private boolean tryBeginGrant(Long userId, String kind, String bizKey, String rewardType,
                                  Integer rewardValue, String rewardLabel, Long relatedId) {
        CheckinGrantLog row = new CheckinGrantLog();
        row.setUserId(userId);
        row.setGrantKind(kind);
        row.setBizKey(bizKey);
        row.setRewardType(rewardType);
        row.setRewardValue(rewardValue);
        row.setRewardLabel(rewardLabel);
        row.setRelatedId(relatedId);
        try {
            checkinGrantLogMapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private void addMakeupCards(Long userId, int amount) {
        if (amount <= 0) {
            return;
        }
        UserCheckinInfo info = loadUserInfo(userId);
        if (info == null) {
            UserCheckinInfo created = new UserCheckinInfo();
            created.setUserId(userId);
            created.setTotalDays(0);
            created.setStreakDays(0);
            created.setTotalPoints(0);
            created.setMakeupCardCount(amount);
            try {
                userCheckinInfoMapper.insert(created);
                return;
            } catch (DuplicateKeyException e) {
                info = loadUserInfo(userId);
            }
        }
        userCheckinInfoMapper.update(null, new LambdaUpdateWrapper<UserCheckinInfo>()
                .eq(UserCheckinInfo::getUserId, userId)
                .setSql("makeup_card_count = IFNULL(makeup_card_count, 0) + " + amount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantMakeupCards(Long userId, int amount) {
        if (userId == null || userId <= 0 || amount <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        addMakeupCards(userId, amount);
    }

    private boolean consumeMakeupCard(Long userId) {
        UserCheckinInfo info = loadUserInfo(userId);
        if (info == null) {
            return false;
        }
        int current = info.getMakeupCardCount() == null ? 0 : info.getMakeupCardCount();
        if (current < 1) {
            return false;
        }
        int updated = userCheckinInfoMapper.update(null, new LambdaUpdateWrapper<UserCheckinInfo>()
                .eq(UserCheckinInfo::getUserId, userId)
                .ge(UserCheckinInfo::getMakeupCardCount, 1)
                .setSql("makeup_card_count = makeup_card_count - " + 1));
        return updated > 0;
    }

    private int recalcStreakFromLogs(Long userId, LocalDate today) {
        List<CheckinLog> recent = checkinLogMapper.selectList(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1)
                .ge(CheckinLog::getCheckinDate, toDate(today.minusDays(400)))
                .orderByDesc(CheckinLog::getCheckinDate));
        Set<LocalDate> signed = new HashSet<>();
        for (CheckinLog row : recent) {
            LocalDate d = toLocalDate(row.getCheckinDate());
            if (d != null) {
                signed.add(d);
            }
        }
        // 今天和昨天都没签：连续为 0 补签过去某天且今天未签时，从今天往前会断
        // 但仍要从「最近连续段」末端算：若昨天没签但补签了更早，streak 以今天为锚则为 0
        int streak = 0;
        LocalDate cursor = signed.contains(today) ? today : today.minusDays(1);
        if (!signed.contains(cursor)) {
            return 0;
        }
        while (signed.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private List<CheckinStreakRewardItemVO> buildStreakRewardItems(int streakDays) {
        List<CheckinStreakRewardItemVO> list = new ArrayList<>();
        for (CheckinStreakReward r : rewardCache) {
            int threshold = r.getStreakDays() == null ? 0 : r.getStreakDays();
            boolean achieved = streakDays >= threshold;
            list.add(new CheckinStreakRewardItemVO(
                    threshold,
                    r.getRewardType(),
                    r.getTitle(),
                    r.getSubtitle(),
                    achieved,
                    achieved ? 0 : threshold - streakDays));
        }
        return list;
    }

    private CheckinResultResponse buildResultResponse(UserCheckinInfo after, int pointsToday,
                                                      int bonusPoints, String bonusDescription,
                                                      SurpriseRoll surprise, LocalDate eventDay) {
        int streak = after.getStreakDays() == null ? 0 : after.getStreakDays();
        return new CheckinResultResponse(
                pointsToday,
                bonusPoints,
                bonusDescription,
                streak,
                after.getTotalDays(),
                after.getTotalPoints(),
                toDate(eventDay),
                after.getMakeupCardCount() == null ? 0 : after.getMakeupCardCount(),
                surprise == null ? null : surprise.type(),
                surprise == null ? null : surprise.value(),
                surprise == null ? null : surprise.label(),
                buildStreakRewardItems(streak));
    }

    private UserCheckinInfo loadUserInfo(Long userId) {
        return userCheckinInfoMapper.selectOne(new LambdaQueryWrapper<UserCheckinInfo>()
                .eq(UserCheckinInfo::getUserId, userId)
                .ne(UserCheckinInfo::getDeleteState, 1));
    }

    private CheckinStreakReward findReward(int streakDays) {
        for (CheckinStreakReward r : rewardCache) {
            if (r.getStreakDays() != null && r.getStreakDays() == streakDays) {
                return r;
            }
        }
        return null;
    }

    private boolean isSurpriseDay(LocalDate day) {
        return resolveDayRule(day).surprise();
    }

    private int resolvePoints(LocalDate day) {
        return resolveDayRule(day).points();
    }

    private DayRule resolveDayRule(LocalDate day) {
        int m = day.getMonthValue();
        int d = day.getDayOfMonth();
        Map<Integer, DayRule> monthRule = ruleCache.get(m);
        if (monthRule != null && monthRule.containsKey(d)) {
            return monthRule.get(d);
        }
        Map<Integer, DayRule> defaultRule = ruleCache.get(DEFAULT_RULE_MONTH);
        if (defaultRule != null && defaultRule.containsKey(d)) {
            return defaultRule.get(d);
        }
        return new DayRule(10, false);
    }

    private UserCheckinInfo upsertUserCheckinInfoAfterEvent(UserCheckinInfo existing, Long userId,
                                                            int newStreak, int addPoints) {
        LocalDate today = LocalDate.now(SHANGHAI);
        Date todayDate = toDate(today);
        // last_checkin 取流水中最大日期
        Page<CheckinLog> latestPage = new Page<>(1, 1, false);
        List<CheckinLog> latestRows = checkinLogMapper.selectPage(latestPage, new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1)
                .orderByDesc(CheckinLog::getCheckinDate)
                .orderByDesc(CheckinLog::getId)).getRecords();
        CheckinLog latest = latestRows.isEmpty() ? null : latestRows.get(0);
        Date lastCheckin = latest == null ? todayDate : latest.getCheckinDate();
        long totalDays = checkinLogMapper.selectCount(new LambdaQueryWrapper<CheckinLog>()
                .eq(CheckinLog::getUserId, userId)
                .ne(CheckinLog::getDeleteState, 1));

        if (existing == null) {
            UserCheckinInfo created = new UserCheckinInfo();
            created.setUserId(userId);
            created.setTotalDays((int) totalDays);
            created.setStreakDays(newStreak);
            created.setTotalPoints(Math.max(0, addPoints));
            created.setLastCheckin(lastCheckin);
            created.setMakeupCardCount(0);
            try {
                userCheckinInfoMapper.insert(created);
                return created;
            } catch (DuplicateKeyException e) {
                existing = loadUserInfo(userId);
                if (existing == null) {
                    throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
                }
            }
        }
        int newTotalPoints = (existing.getTotalPoints() == null ? 0 : existing.getTotalPoints()) + Math.max(0, addPoints);
        int cards = existing.getMakeupCardCount() == null ? 0 : existing.getMakeupCardCount();
        userCheckinInfoMapper.update(null, new LambdaUpdateWrapper<UserCheckinInfo>()
                .eq(UserCheckinInfo::getUserId, userId)
                .set(UserCheckinInfo::getTotalDays, (int) totalDays)
                .set(UserCheckinInfo::getStreakDays, newStreak)
                .set(UserCheckinInfo::getTotalPoints, newTotalPoints)
                .set(UserCheckinInfo::getLastCheckin, lastCheckin));
        existing.setTotalDays((int) totalDays);
        existing.setStreakDays(newStreak);
        existing.setTotalPoints(newTotalPoints);
        existing.setLastCheckin(lastCheckin);
        existing.setMakeupCardCount(cards);
        // 刷新补签卡 可能在发奖过程中已变
        UserCheckinInfo refreshed = loadUserInfo(userId);
        if (refreshed != null) {
            existing.setMakeupCardCount(refreshed.getMakeupCardCount());
        }
        return existing;
    }

    private static Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(SHANGHAI).toInstant());
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(SHANGHAI).toLocalDate();
    }

    private static long secondsUntilNextMidnight() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(SHANGHAI);
        return Math.max(1L, Duration.between(now, nextMidnight).getSeconds());
    }

    private void invalidateStatusCache(Long userId) {
        try {
            stringRedisTemplate.delete(Constant.REDIS_KEY_CHECKIN_STATUS + userId);
            // 规则缓存可能含惊喜标记，月切换时由 TTL 自然过期即可
        } catch (Exception e) {
            log.error("失效签到状态缓存失败, userId: {}", userId, e);
        }
    }
}
