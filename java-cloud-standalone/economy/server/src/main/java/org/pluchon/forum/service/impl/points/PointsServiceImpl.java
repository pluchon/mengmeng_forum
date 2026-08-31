package org.pluchon.forum.service.impl.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.cloud.ForumDomainNames;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.entity.db.PointsLog;
import org.pluchon.forum.entity.db.PointsMilestoneClaim;
import org.pluchon.forum.entity.db.PointsWallet;
import org.pluchon.forum.entity.dto.points.PointsLogQueryDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.points.PointsCenterOverviewVO;
import org.pluchon.forum.entity.vo.points.PointsCenterChartVO;
import org.pluchon.forum.entity.vo.points.PointsCenterTrendVO;
import org.pluchon.forum.entity.vo.points.PointsDailyVO;
import org.pluchon.forum.entity.vo.points.PointsLogVO;
import org.pluchon.forum.entity.vo.points.PointsMilestoneVO;
import org.pluchon.forum.entity.vo.points.PointsSourceSummaryVO;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;
import org.pluchon.forum.mapper.PointsLogMapper;
import org.pluchon.forum.mapper.PointsMilestoneClaimMapper;
import org.pluchon.forum.mapper.PointsWalletMapper;
import org.pluchon.forum.service.impl.user.UserDerivedCacheInvalidator;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.pluchon.forum.common.constant.ForumTimeZone;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

// 仅 economy 域本地落库；其他域走 PointsFeignService
@Service
@Slf4j
@ConditionalOnProperty(name = "forum.domain", havingValue = ForumDomainNames.ECONOMY)
public class PointsServiceImpl implements PointsService {

    private static final ZoneId SHANGHAI = ForumTimeZone.ZONE_ID;
    private static final List<MilestoneDefinition> MILESTONES = List.of(
            new MilestoneDefinition("M1000", 1000, 50, "萌新旅人"),
            new MilestoneDefinition("M2000", 2000, 200, "软萌收藏家"),
            new MilestoneDefinition("M5000", 5000, 500, "星光旅人"),
            new MilestoneDefinition("M10000", 10000, 2000, "萌币传说")
    );

    @Autowired
    private PointsWalletMapper pointsWalletMapper;

    @Autowired
    @Lazy
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Autowired
    private PointsMilestoneClaimMapper pointsMilestoneClaimMapper;

    @Autowired
    private UserDerivedCacheInvalidator userDerivedCacheInvalidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        return addPoints(userId, amount, sourceType, relatedId, remark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        return deductPoints(userId, amount, sourceType, relatedId, remark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey) {
        validateAmount(userId, amount, sourceType);
        ensureWalletForUpdate(userId);
        Integer existingBalance = resolveExistingBalance(userId, idempotencyKey);
        if (existingBalance != null) {
            return existingBalance;
        }
        int affected = pointsWalletMapper.addBalance(userId, amount);
        if (affected != 1) {
            log.warn("加积分失败: userId={}, amount={}, affected={}", userId, amount, affected);
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        int balanceAfter = selectBalance(userId);
        insertLog(userId, amount, balanceAfter, sourceType, relatedId, remark, idempotencyKey);
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
        return balanceAfter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey) {
        validateAmount(userId, amount, sourceType);
        ensureWalletForUpdate(userId);
        Integer existingBalance = resolveExistingBalance(userId, idempotencyKey);
        if (existingBalance != null) {
            return existingBalance;
        }
        int affected = pointsWalletMapper.deductBalance(userId, amount);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_POINTS_NOT_ENOUGH));
        }
        int balanceAfter = selectBalance(userId);
        insertLog(userId, -amount, balanceAfter, sourceType, relatedId, remark, idempotencyKey);
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
        return balanceAfter;
    }

    @Override
    public boolean hasIdempotencyRecord(Long userId, String idempotencyKey) {
        return findByIdempotencyKey(userId, idempotencyKey) != null;
    }

    @Override
    public PointsWalletVO getWallet(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return new PointsWalletVO(selectBalance(userId));
    }

    @Override
    public PointsCenterOverviewVO getCenterOverview(Long userId, Integer weekOffset) {
        validateUserId(userId);
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate monthStart = today.withDayOfMonth(1);
        PointsCenterTrendVO trend = getCenterTrend(userId, weekOffset);

        Date monthFrom = Date.from(monthStart.atStartOfDay(SHANGHAI).toInstant());
        Date monthTo = Date.from(today.plusDays(1).atStartOfDay(SHANGHAI).toInstant());
        List<PointsDailyVO> monthTrend = toDailyRows(pointsLogMapper.selectDailyAggregation(userId, monthFrom, monthTo));
        int monthIncome = monthTrend.stream().mapToInt(item -> safeInt(item.getInTotal())).sum();
        int monthExpense = monthTrend.stream().mapToInt(item -> safeInt(item.getOutTotal())).sum();
        int cumulativeIncome = cumulativeIncome(userId);

        PointsCenterOverviewVO overview = new PointsCenterOverviewVO();
        overview.setBalance(selectBalance(userId));
        overview.setMonthIncome(monthIncome);
        overview.setMonthExpense(monthExpense);
        overview.setTrendStartDate(trend.getTrendStartDate());
        overview.setTrendEndDate(trend.getTrendEndDate());
        overview.setHasPreviousWeek(trend.getHasPreviousWeek());
        overview.setHasNextWeek(trend.getHasNextWeek());
        overview.setTrendWeekComplete(trend.getTrendWeekComplete());
        overview.setDailyTrend(trend.getDailyTrend());
        overview.setCumulativeIncome(cumulativeIncome);
        overview.setMilestones(buildMilestones(userId, cumulativeIncome));
        overview.setIncomeSources(toSourceSummaries(pointsLogMapper.selectTopSources(userId, monthFrom, monthTo, 1)));
        overview.setExpenseSources(toSourceSummaries(pointsLogMapper.selectTopSources(userId, monthFrom, monthTo, 0)));
        return overview;
    }

    @Override
    public PointsCenterTrendVO getCenterTrend(Long userId, Integer weekOffset) {
        validateUserId(userId);
        LocalDate today = LocalDate.now(SHANGHAI);
        TrendWeek trendWeek = resolveTrendWeek(userId, today, weekOffset);
        Date from = Date.from(trendWeek.startDate().atStartOfDay(SHANGHAI).toInstant());
        LocalDate queryEndDate = trendWeek.endDate().isAfter(today) ? today : trendWeek.endDate();
        Date to = Date.from(queryEndDate.plusDays(1).atStartOfDay(SHANGHAI).toInstant());

        PointsCenterTrendVO trend = new PointsCenterTrendVO();
        trend.setTrendStartDate(trendWeek.startDate().toString());
        trend.setTrendEndDate(trendWeek.endDate().toString());
        trend.setHasPreviousWeek(trendWeek.hasPreviousWeek());
        trend.setHasNextWeek(trendWeek.hasNextWeek());
        trend.setTrendWeekComplete(!trendWeek.endDate().isAfter(today));
        trend.setDailyTrend(toCompleteDailyTrend(
                pointsLogMapper.selectDailyAggregation(userId, from, to),
                trendWeek.startDate(), trendWeek.endDate()));
        return trend;
    }

    @Override
    public PointsCenterChartVO getCenterChart(Long userId, PointsLogQueryDTO query) {
        validateUserId(userId);
        PointsLogQueryDTO safeQuery = query == null ? new PointsLogQueryDTO() : query;
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate start = resolveLogStartDate(today, safeQuery.getTimeRange());
        Date from = Date.from(start.atStartOfDay(SHANGHAI).toInstant());
        Date to = Date.from(today.plusDays(1).atStartOfDay(SHANGHAI).toInstant());
        List<Map<String, Object>> rows = pointsLogMapper.selectSourceAggregation(
                userId, from, to, normalizeDirection(safeQuery.getDirection()), safeQuery.getSourceType());

        List<PointsSourceSummaryVO> incomeSources = new ArrayList<>();
        List<PointsSourceSummaryVO> expenseSources = new ArrayList<>();
        int incomeTotal = 0;
        int expenseTotal = 0;
        for (Map<String, Object> row : rows) {
            byte sourceType = (byte) safeInt(row.get("source_type"));
            int income = safeInt(row.get("in_total"));
            int expense = safeInt(row.get("out_total"));
            if (income > 0) {
                incomeSources.add(toSourceSummary(sourceType, income));
                incomeTotal += income;
            }
            if (expense > 0) {
                expenseSources.add(toSourceSummary(sourceType, expense));
                expenseTotal += expense;
            }
        }
        PointsCenterChartVO chart = new PointsCenterChartVO();
        chart.setIncomeTotal(incomeTotal);
        chart.setExpenseTotal(expenseTotal);
        chart.setIncomeSources(incomeSources);
        chart.setExpenseSources(expenseSources);
        return chart;
    }

    private TrendWeek resolveTrendWeek(Long userId, LocalDate today, Integer weekOffset) {
        // 注册日只用来定周区间的左边界，auth 域抖动时退回本周，别让整个萌币中心打不开
        LocalDate registrationDate = today;
        try {
            UserInternalVO user = userInternalFeignClient.getById(userId);
            if (user != null && user.getCreateTime() != null) {
                registrationDate = user.getCreateTime().toInstant().atZone(SHANGHAI).toLocalDate();
            }
        } catch (Exception e) {
            log.warn("拉取注册时间失败，萌币足迹按本周展示 userId={}", userId, e);
        }
        if (registrationDate.isAfter(today)) {
            registrationDate = today;
        }
        LocalDate firstCalendarWeekStart = registrationDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentCalendarWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int currentWeekIndex = (int) ChronoUnit.WEEKS.between(firstCalendarWeekStart, currentCalendarWeekStart);
        int requestedOffset = weekOffset == null ? 0 : weekOffset;
        int targetWeekIndex = Math.max(0, Math.min(currentWeekIndex, currentWeekIndex + requestedOffset));
        LocalDate startDate = firstCalendarWeekStart.plusWeeks(targetWeekIndex);
        LocalDate endDate = startDate.plusDays(6);
        return new TrendWeek(startDate, endDate, targetWeekIndex > 0, targetWeekIndex < currentWeekIndex);
    }

    @Override
    public PageResult<PointsLogVO> getCenterLogWithPage(Long userId, PointsLogQueryDTO query) {
        validateUserId(userId);
        PointsLogQueryDTO safeQuery = query == null ? new PointsLogQueryDTO() : query;
        int pageNum = PageUtils.getValidPageNum(safeQuery.getPageNum());
        int pageSize = PageUtils.getValidPageSize(safeQuery.getPageSize());
        Page<PointsLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PointsLog> wrapper = new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getUserId, userId)
                .ne(PointsLog::getDeleteState, 1);
        applyCenterLogFilter(wrapper, safeQuery);
        Page<PointsLog> result = pointsLogMapper.selectPage(page, wrapper
                .orderByDesc(PointsLog::getCreateTime)
                .orderByDesc(PointsLog::getId));
        List<PointsLogVO> records = new ArrayList<>(result.getRecords().size());
        for (PointsLog row : result.getRecords()) {
            records.add(toLogVO(row));
        }
        return new PageResult<>(records, result.getTotal(), pageNum, pageSize, result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int claimMilestone(Long userId, String milestoneCode) {
        validateUserId(userId);
        MilestoneDefinition milestone = findMilestone(milestoneCode);
        int cumulativeIncome = cumulativeIncome(userId);
        if (cumulativeIncome < milestone.threshold()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "尚未达到该萌币里程碑"));
        }
        PointsMilestoneClaim existing = pointsMilestoneClaimMapper.selectOne(new LambdaQueryWrapper<PointsMilestoneClaim>()
                .eq(PointsMilestoneClaim::getUserId, userId)
                .eq(PointsMilestoneClaim::getMilestoneCode, milestone.code())
                .ne(PointsMilestoneClaim::getDeleteState, 1));
        if (existing != null) {
            return selectBalance(userId);
        }

        PointsMilestoneClaim claim = new PointsMilestoneClaim();
        claim.setUserId(userId);
        claim.setMilestoneCode(milestone.code());
        claim.setRewardAmount(milestone.reward());
        claim.setDeleteState(0);
        try {
            pointsMilestoneClaimMapper.insert(claim);
        } catch (DuplicateKeyException ex) {
            return selectBalance(userId);
        }
        return addPoints(userId, milestone.reward(), Constant.POINTS_SOURCE_MILESTONE_REWARD,
                claim.getId(), "萌币里程碑奖励 · " + milestone.title(),
                "points-milestone:" + userId + ":" + milestone.code());
    }

    // 里程碑的「累计获得」口径：只算外部挣来的萌币，里程碑奖励本身不计入
    private int cumulativeIncome(Long userId) {
        return safeInt(pointsLogMapper.sumPositiveExcluding(userId, Constant.POINTS_SOURCE_MILESTONE_REWARD));
    }

    private List<PointsMilestoneVO> buildMilestones(Long userId, int cumulativeIncome) {
        Set<String> claimedCodes = new HashSet<>();
        List<PointsMilestoneClaim> claims = pointsMilestoneClaimMapper.selectList(new LambdaQueryWrapper<PointsMilestoneClaim>()
                .eq(PointsMilestoneClaim::getUserId, userId)
                .ne(PointsMilestoneClaim::getDeleteState, 1));
        for (PointsMilestoneClaim claim : claims) {
            claimedCodes.add(claim.getMilestoneCode());
        }
        List<PointsMilestoneVO> rows = new ArrayList<>(MILESTONES.size());
        for (MilestoneDefinition item : MILESTONES) {
            PointsMilestoneVO row = new PointsMilestoneVO();
            row.setCode(item.code());
            row.setThreshold(item.threshold());
            row.setReward(item.reward());
            row.setTitle(item.title());
            row.setStatus(claimedCodes.contains(item.code()) ? "CLAIMED"
                    : (cumulativeIncome >= item.threshold() ? "CLAIMABLE" : "LOCKED"));
            rows.add(row);
        }
        return rows;
    }

    private List<PointsSourceSummaryVO> toSourceSummaries(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PointsSourceSummaryVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            byte sourceType = (byte) safeInt(row.get("source_type"));
            PointsSourceSummaryVO item = new PointsSourceSummaryVO();
            item.setSourceType(sourceType);
            item.setSourceLabel(sourceLabel(sourceType));
            item.setAmount(safeInt(row.get("amount")));
            result.add(item);
        }
        return result;
    }

    private List<PointsDailyVO> toDailyRows(List<Map<String, Object>> raw) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(ForumTimeZone.timeZone());
        List<PointsDailyVO> list = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) {
            Object dayObj = row.get("day");
            String day = dayObj instanceof Date ? fmt.format((Date) dayObj) : String.valueOf(dayObj);
            list.add(new PointsDailyVO(day, safeInt(row.get("in_total")), safeInt(row.get("out_total")), safeInt(row.get("net"))));
        }
        return list;
    }

    private List<PointsDailyVO> toCompleteDailyTrend(List<Map<String, Object>> raw,
                                                      LocalDate startDate,
                                                      LocalDate endDate) {
        Map<LocalDate, PointsDailyVO> dailyRows = new HashMap<>();
        for (PointsDailyVO item : toDailyRows(raw)) {
            dailyRows.put(LocalDate.parse(item.getDay()), item);
        }
        List<PointsDailyVO> result = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            result.add(dailyRows.getOrDefault(day, new PointsDailyVO(day.toString(), 0, 0, 0)));
        }
        return result;
    }

    private void applyCenterLogFilter(LambdaQueryWrapper<PointsLog> wrapper, PointsLogQueryDTO query) {
        String direction = normalizeDirection(query.getDirection());
        if ("INCOME".equals(direction)) {
            wrapper.gt(PointsLog::getDelta, 0);
        } else if ("EXPENSE".equals(direction)) {
            wrapper.lt(PointsLog::getDelta, 0);
        }
        if (query.getSourceType() != null) {
            wrapper.eq(PointsLog::getSourceType, query.getSourceType());
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate start = resolveLogStartDate(today, query.getTimeRange());
        wrapper.ge(PointsLog::getCreateTime, Date.from(start.atStartOfDay(SHANGHAI).toInstant()))
                .lt(PointsLog::getCreateTime, Date.from(today.plusDays(1).atStartOfDay(SHANGHAI).toInstant()));
    }

    private String normalizeDirection(String direction) {
        String value = direction == null ? "ALL" : direction.trim().toUpperCase(Locale.ROOT);
        return "INCOME".equals(value) || "EXPENSE".equals(value) ? value : "ALL";
    }

    private String normalizeTimeRange(String range) {
        String value = range == null ? "LAST_30_DAYS" : range.trim().toUpperCase(Locale.ROOT);
        return "LAST_7_DAYS".equals(value) || "THIS_MONTH".equals(value) ? value : "LAST_30_DAYS";
    }

    private LocalDate resolveLogStartDate(LocalDate today, String range) {
        return switch (normalizeTimeRange(range)) {
            case "LAST_7_DAYS" -> today.minusDays(6);
            case "THIS_MONTH" -> today.withDayOfMonth(1);
            default -> today.minusDays(29);
        };
    }

    private PointsSourceSummaryVO toSourceSummary(byte sourceType, int amount) {
        PointsSourceSummaryVO item = new PointsSourceSummaryVO();
        item.setSourceType(sourceType);
        item.setSourceLabel(sourceLabel(sourceType));
        item.setAmount(amount);
        return item;
    }

    private MilestoneDefinition findMilestone(String code) {
        for (MilestoneDefinition item : MILESTONES) {
            if (item.code().equalsIgnoreCase(code == null ? "" : code.trim())) {
                return item;
            }
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS, "萌币里程碑不存在"));
    }

    private record TrendWeek(LocalDate startDate, LocalDate endDate, boolean hasPreviousWeek, boolean hasNextWeek) {
    }

    private PointsLogVO toLogVO(PointsLog row) {
        return new PointsLogVO(row.getId(), row.getDelta(), row.getBalanceAfter(),
                row.getSourceType(), row.getRelatedId(), row.getRemark(), row.getCreateTime());
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeInt(Object value) {
        return toInt(value);
    }

    private String sourceLabel(byte sourceType) {
        return switch (sourceType) {
            case 0 -> "签到";
            case 1 -> "连续签到奖励";
            case 2 -> "表情商城";
            case 3 -> "退款回补";
            case 4 -> "积分抽奖";
            case 5 -> "积分抽奖中奖";
            case 6 -> "注册赠送";
            case 7 -> "会员订阅";
            case 9 -> "看板娘陪伴";
            case 10 -> "AI 生图";
            case 11 -> "游戏胜利";
            case 12 -> "游戏失败";
            case 13 -> "俄罗斯方块";
            case 14 -> "签到惊喜奖励";
            case 15 -> "萌币里程碑奖励";
            case 16 -> "幸运收集册奖励";
            case 99 -> "管理员调整";
            default -> "其他来源";
        };
    }

    private record MilestoneDefinition(String code, int threshold, int reward, String title) {
    }

    private void validateAmount(Long userId, int amount, Byte sourceType) {
        if (userId == null || userId <= 0 || amount <= 0 || sourceType == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private Integer resolveExistingBalance(Long userId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        // 钱包行锁之后用当前读，避免 RR 快照读不到已提交流水导致双加
        PointsLog existing = findByIdempotencyKeyForUpdate(userId, idempotencyKey.trim());
        if (existing != null) {
            return existing.getBalanceAfter();
        }
        return null;
    }

    private PointsLog findByIdempotencyKey(Long userId, String idempotencyKey) {
        if (userId == null || !StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return pointsLogMapper.selectOne(new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getUserId, userId)
                .eq(PointsLog::getIdempotencyKey, idempotencyKey)
                .ne(PointsLog::getDeleteState, 1)
                .last("LIMIT 1"));
    }

    private PointsLog findByIdempotencyKeyForUpdate(Long userId, String idempotencyKey) {
        if (userId == null || !StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return pointsLogMapper.selectOne(new LambdaQueryWrapper<PointsLog>()
                .eq(PointsLog::getUserId, userId)
                .eq(PointsLog::getIdempotencyKey, idempotencyKey)
                .ne(PointsLog::getDeleteState, 1)
                .last("LIMIT 1 FOR UPDATE"));
    }

    private int selectBalance(Long userId) {
        PointsWallet wallet = pointsWalletMapper.selectByUserId(userId);
        if (wallet != null) {
            return wallet.getBalance() == null ? 0 : wallet.getBalance();
        }
        ensureWalletExists(userId);
        wallet = pointsWalletMapper.selectByUserId(userId);
        if (wallet == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return wallet.getBalance() == null ? 0 : wallet.getBalance();
    }

    // 无锁建档，供只读路径与写路径共用
    private void ensureWalletExists(Long userId) {
        if (pointsWalletMapper.selectByUserId(userId) != null) {
            return;
        }
        Boolean exists = userInternalFeignClient.existsById(userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        try {
            pointsWalletMapper.insertWallet(userId, 0);
        } catch (DuplicateKeyException ignored) {
            // 并发建档忽略
        }
    }

    private void ensureWalletForUpdate(Long userId) {
        ensureWalletExists(userId);
        PointsWallet locked = pointsWalletMapper.selectByUserIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
    }

    private void insertLog(Long userId, int delta, int balanceAfter, Byte sourceType, Long relatedId,
                           String remark, String idempotencyKey) {
        PointsLog row = new PointsLog();
        row.setUserId(userId);
        row.setDelta(delta);
        row.setBalanceAfter(balanceAfter);
        row.setSourceType(sourceType);
        row.setRelatedId(relatedId);
        row.setRemark(remark);
        if (StringUtils.hasText(idempotencyKey)) {
            row.setIdempotencyKey(idempotencyKey.trim());
        }
        try {
            pointsLogMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            // 余额可能已变更：必须回滚事务，禁止吞掉唯一键冲突后仍返回成功
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "积分记账冲突，请重试"));
        }
    }


    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
