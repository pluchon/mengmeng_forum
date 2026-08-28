package org.pluchon.forum.service.impl.lottery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.api.content.UserDailyEngagementInternalVO;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.economy.client.EconomyUserEngagementInternalFeignClient;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.entity.db.LotteryActivity;
import org.pluchon.forum.entity.db.LotteryCollectMilestone;
import org.pluchon.forum.entity.db.LotteryDrawRecord;
import org.pluchon.forum.entity.db.LotteryDrawRequest;
import org.pluchon.forum.entity.db.LotteryPoolTask;
import org.pluchon.forum.entity.db.LotteryPrizeMysteryItem;
import org.pluchon.forum.entity.db.LotteryVoucherLog;
import org.pluchon.forum.entity.db.UserCheckinInfo;
import org.pluchon.forum.entity.db.UserLotteryCollectClaim;
import org.pluchon.forum.entity.db.UserLotteryCollectOwned;
import org.pluchon.forum.entity.db.UserLotteryPity;
import org.pluchon.forum.entity.db.UserLotteryTaskClaim;
import org.pluchon.forum.entity.db.UserLotteryVoucher;
import org.pluchon.forum.entity.dto.lottery.LotteryCollectClaimDTO;
import org.pluchon.forum.entity.dto.lottery.LotteryDrawDTO;
import org.pluchon.forum.entity.dto.lottery.LotteryTaskClaimDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityInfoVO;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityListItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryCollectMilestoneVO;
import org.pluchon.forum.entity.vo.lottery.LotteryCollectVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawHistoryVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawRecordVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawResultVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPoolTaskVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPrizeLineVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPrizePoolRow;
import org.pluchon.forum.entity.vo.lottery.LotteryPublicRecentDrawVO;
import org.pluchon.forum.entity.vo.lottery.LotteryRecentDrawVO;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;
import org.pluchon.forum.mapper.LotteryActivityMapper;
import org.pluchon.forum.mapper.LotteryActivityPrizeMapper;
import org.pluchon.forum.mapper.LotteryCollectMilestoneMapper;
import org.pluchon.forum.mapper.LotteryDrawHourlyStatMapper;
import org.pluchon.forum.mapper.LotteryDrawRecordMapper;
import org.pluchon.forum.mapper.LotteryDrawRequestMapper;
import org.pluchon.forum.mapper.LotteryPoolTaskMapper;
import org.pluchon.forum.mapper.LotteryPrizeMysteryItemMapper;
import org.pluchon.forum.mapper.LotteryVoucherLogMapper;
import org.pluchon.forum.mapper.UserCheckinInfoMapper;
import org.pluchon.forum.mapper.UserLotteryCollectClaimMapper;
import org.pluchon.forum.mapper.UserLotteryCollectOwnedMapper;
import org.pluchon.forum.mapper.UserLotteryPityMapper;
import org.pluchon.forum.mapper.UserLotteryTaskClaimMapper;
import org.pluchon.forum.mapper.UserLotteryVoucherMapper;
import org.pluchon.forum.service.impl.lottery.guard.LotteryDrawContext;
import org.pluchon.forum.service.impl.lottery.guard.LotteryDrawGuardChain;
import org.pluchon.forum.service.impl.lottery.guard.LotteryDrawGuardResult;
import org.pluchon.forum.service.impl.starlight.StarlightServiceImpl;
import org.pluchon.forum.service.interfaces.lottery.LotteryService;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.starlight.StarlightService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LotteryServiceImpl implements LotteryService {

    private static final int MAX_STOCK_RETRY = 64;

    private static final int DEFAULT_RECORD_PAGE_SIZE = 10;

    // 公开中奖流：每页默认 5 条，最多 5 页 窗口 25
    private static final int DEFAULT_PUBLIC_PAGE_SIZE = 5;

    private static final int PUBLIC_MAX_PAGES = 5;

    private static final int PUBLIC_RECENT_WINDOW = DEFAULT_PUBLIC_PAGE_SIZE * PUBLIC_MAX_PAGES;

    // 卡池列表默认每页 4 条
    private static final int DEFAULT_ACTIVITY_PAGE_SIZE = 5;

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Taipei");

    @Autowired
    private LotteryActivityMapper lotteryActivityMapper;

    @Autowired
    private LotteryActivityPrizeMapper lotteryActivityPrizeMapper;

    @Autowired
    private LotteryDrawRecordMapper lotteryDrawRecordMapper;

    @Autowired
    private LotteryDrawRequestMapper lotteryDrawRequestMapper;

    @Autowired
    private LotteryDrawHourlyStatMapper lotteryDrawHourlyStatMapper;

    @Autowired
    private LotteryPrizeMysteryItemMapper lotteryPrizeMysteryItemMapper;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private EconomyUserInternalFeignClient userInternalFeignClient;

    @Autowired
    private UserLotteryPityMapper userLotteryPityMapper;

    @Autowired
    private UserLotteryVoucherMapper userLotteryVoucherMapper;

    @Autowired
    private LotteryVoucherLogMapper lotteryVoucherLogMapper;

    @Autowired
    private LotteryPoolTaskMapper lotteryPoolTaskMapper;

    @Autowired
    private UserLotteryTaskClaimMapper userLotteryTaskClaimMapper;

    @Autowired
    private LotteryCollectMilestoneMapper lotteryCollectMilestoneMapper;

    @Autowired
    private UserLotteryCollectOwnedMapper userLotteryCollectOwnedMapper;

    @Autowired
    private UserLotteryCollectClaimMapper userLotteryCollectClaimMapper;

    @Autowired
    private UserCheckinInfoMapper userCheckinInfoMapper;

    @Autowired
    private EconomyUserEngagementInternalFeignClient userEngagementInternalFeignClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private StarlightService starlightService;

    private final LotteryDrawGuardChain lotteryDrawGuardChain = LotteryDrawGuardChain.defaultChain();

    @Override
    public PageResult<LotteryActivityListItemVO> pageSelectableActivities(Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requested = pageSize == null || pageSize < 1 ? DEFAULT_ACTIVITY_PAGE_SIZE : pageSize;
        int validPageSize = Math.min(PageUtils.getValidPageSize(requested), DEFAULT_ACTIVITY_PAGE_SIZE);
        Date now = new Date();
        Page<LotteryActivity> page = new Page<>(validPageNum, validPageSize);
        Page<LotteryActivity> result = lotteryActivityMapper.selectPage(
                page,
                Wrappers.lambdaQuery(LotteryActivity.class)
                        .eq(LotteryActivity::getDeleteState, 0)
                        .eq(LotteryActivity::getStatus, (byte) 1)
                        .eq(LotteryActivity::getPhase, (byte) 1)
                        .and(w -> w.isNull(LotteryActivity::getStartTime)
                                .or()
                                .le(LotteryActivity::getStartTime, now))
                        .and(w -> w.isNull(LotteryActivity::getEndTime)
                                .or()
                                .ge(LotteryActivity::getEndTime, now))
                        .orderByDesc(LotteryActivity::getId));
        List<LotteryActivityListItemVO> records = result.getRecords().stream()
                .filter(this::isActivityValid)
                .map(a -> new LotteryActivityListItemVO(
                        a.getId(),
                        a.getTitle(),
                        a.getCoverImageUrl(),
                        a.getCostPointsPerDraw(),
                        a.getEndTime()))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public LotteryActivityInfoVO getActivityInfo(Long userId, Long activityId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryActivity activity = resolveActivity(activityId);
        // GET 只读：里程奖励在抽奖写路径或显式 claim 接口发放，避免刷新页面触发写副作用
        PointsWalletVO wallet = pointsService.getWallet(userId);
        List<LotteryPrizePoolRow> rows = lotteryActivityPrizeMapper.selectDrawablePool(activity.getId());
        List<LotteryPrizeLineVO> lines = rows.stream()
                .map(r -> {
                    LotteryPrizeLineVO line = new LotteryPrizeLineVO();
                    line.setName(r.getPrizeName());
                    line.setPrizeType(r.getPrizeType());
                    line.setPrizeValue(r.getPrizeValue());
                    line.setStockRemaining(r.getStockRemaining());
                    line.setJackpot(r.getIsJackpot() != null && r.getIsJackpot() == 1);
                    line.setWeight(r.getWeight());
                    line.setImagePath(r.getImagePath());
                    return line;
                })
                .collect(Collectors.toList());

        List<LotteryRecentDrawVO> recent =
                lotteryDrawRecordMapper.selectRecentForUser(userId, activity.getId(), 12);
        LotteryActivityInfoVO vo = new LotteryActivityInfoVO();
        vo.setActivityId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setDescription(activity.getDescription());
        vo.setCostPointsPerDraw(activity.getCostPointsPerDraw());
        vo.setBalance(wallet.getBalance());
        vo.setPrizes(lines);
        UserLotteryPity pityRow = userLotteryPityMapper.selectByUserId(userId);
        int pityDisplay = pityRow != null && pityRow.getPityDraws() != null ? pityRow.getPityDraws() : 0;
        vo.setPityDrawsSinceJackpot(pityDisplay);
        vo.setHardPityThreshold(Constant.LOTTERY_HARD_PITY_AFTER_MISSES);
        vo.setRecentDraws(recent != null ? recent : List.of());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setVoucherBalance(getVoucherBalance(userId));
        vo.setVoucherOffsetPoints(activity.getCostPointsPerDraw());
        vo.setStarlightBalance(starlightService.getBalance(userId));
        vo.setTasks(buildPoolTaskViews(userId, activity.getId()));
        vo.setCollect(buildCollectView(userId, activity.getId()));
        return vo;
    }

    @Override
    public PageResult<LotteryDrawHistoryVO> queryDrawRecords(Long userId, Long activityId,
                                                             Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requestedPageSize = pageSize == null || pageSize < 1 ? DEFAULT_RECORD_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requestedPageSize);
        Page<LotteryDrawRecord> page = new Page<>(validPageNum, validPageSize);
        Page<LotteryDrawRecord> result = lotteryDrawRecordMapper.selectPage(
                page,
                Wrappers.lambdaQuery(LotteryDrawRecord.class)
                        .eq(LotteryDrawRecord::getUserId, userId)
                        .eq(activityId != null && activityId > 0, LotteryDrawRecord::getActivityId, activityId)
                        .ne(LotteryDrawRecord::getDeleteState, 1)
                        .orderByDesc(LotteryDrawRecord::getCreateTime)
                        .orderByDesc(LotteryDrawRecord::getId));
        List<LotteryDrawHistoryVO> records = buildDrawHistoryRows(userId, result.getRecords());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    private List<LotteryDrawHistoryVO> buildDrawHistoryRows(Long userId, List<LotteryDrawRecord> drawRecords) {
        if (drawRecords == null || drawRecords.isEmpty()) {
            return List.of();
        }
        List<String> batchKeys = drawRecords.stream()
                .map(LotteryDrawRecord::getDrawBatchKey)
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        Map<String, LotteryDrawRequest> requestByBatch = loadDrawRequestByBatchKey(userId, batchKeys);
        List<LotteryDrawHistoryVO> rows = new ArrayList<>(drawRecords.size());
        for (LotteryDrawRecord record : drawRecords) {
            LotteryDrawRequest request = requestByBatch.get(record.getDrawBatchKey());
            LotteryDrawHistoryVO row = new LotteryDrawHistoryVO();
            row.setDrawRecordId(record.getId());
            row.setDrawRequestId(request == null ? null : request.getId());
            row.setPrizeSummary(StringUtils.hasText(record.getPrizeName())
                    ? record.getPrizeName().trim() : "未命名奖品");
            row.setPrizeType(resolveHistoryPrizeType(record));
            row.setRewardSummary(buildSingleRewardSummary(record));
            row.setCostMethod(buildHistoryCostMethod(request));
            row.setCreateTime(record.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, LotteryDrawRequest> loadDrawRequestByBatchKey(Long userId, List<String> batchKeys) {
        if (batchKeys == null || batchKeys.isEmpty()) {
            return Map.of();
        }
        return lotteryDrawRequestMapper.selectList(Wrappers.lambdaQuery(LotteryDrawRequest.class)
                        .eq(LotteryDrawRequest::getUserId, userId)
                        .in(LotteryDrawRequest::getBatchKey, batchKeys)
                        .ne(LotteryDrawRequest::getDeleteState, 1))
                .stream()
                .filter(row -> StringUtils.hasText(row.getBatchKey()))
                .collect(Collectors.toMap(LotteryDrawRequest::getBatchKey, row -> row, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Byte resolveHistoryPrizeType(LotteryDrawRecord record) {
        if (record.getIsJackpot() != null && record.getIsJackpot() == 1) {
            return Constant.LOTTERY_PRIZE_GRAND;
        }
        return record.getPrizeType() == null ? Constant.LOTTERY_PRIZE_THANKS : record.getPrizeType();
    }

    private String buildSingleRewardSummary(LotteryDrawRecord record) {
        int grantPoints = record.getGrantPoints() == null ? 0 : record.getGrantPoints();
        int grantVipDays = 0;
        if (Objects.equals(record.getMysteryItemType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = record.getMysteryItemValue() == null ? 0 : record.getMysteryItemValue();
        } else if (Objects.equals(record.getPrizeType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = record.getPrizeValue() == null ? 0 : record.getPrizeValue();
        }
        String detail = buildRewardDetail(record.getMysteryItemType(), record.getMysteryItemValue(),
                grantPoints, grantVipDays, record.getPrizeType(),
                record.getPrizeValue() == null ? 0 : record.getPrizeValue());
        return StringUtils.hasText(detail) ? detail : "—";
    }

    private String buildHistoryCostMethod(LotteryDrawRequest request) {
        if (request == null) {
            return "历史消耗未记录";
        }
        String base = buildCostMethod(request);
        boolean multi = request.getTimes() != null && request.getTimes() > 1;
        return multi ? ("十连 · " + base) : base;
    }

    private String buildCostMethod(LotteryDrawRequest request) {
        if (request.getVouchersUsed() == null || request.getPointsCharged() == null) {
            return "历史消耗未记录";
        }
        int vouchers = Math.max(0, request.getVouchersUsed());
        int points = Math.max(0, request.getPointsCharged());
        if (vouchers > 0 && points > 0) {
            return points + " 萌币 + " + vouchers + " 张抵扣券";
        }
        if (vouchers > 0) {
            return "使用 " + vouchers + " 张抵扣券";
        }
        return "消耗 " + points + " 萌币";
    }

    // 抽奖主流程： <ul> <li>加权随机：在售库存档位上按 weight 求和抽签 含二分等价线性扫描 。</li> <li>硬保底：{@link Constant#LOTTERY_HARD_PITY_AFTER_MISSES} 次未中神秘大奖父档 is_jackpot 后， 下一次强制仅在该档位抽签 售罄则回落全池并打日志 。计数持久化 user_lottery_pity。</li> <li>软保底 十连 ：若前 9 抽均未命中「稀有」奖品类型 大奖/小奖/VIP天 ，则第 10 抽仅在稀有子池中抽签； 若稀有子池售罄则回落全池。</li> </ul>
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LotteryDrawResultVO draw(Long userId, LotteryDrawDTO dto) {
        checkLotteryDrawGuard(LotteryDrawContext.requestOnly(userId, dto));
        if (dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请求无效，请刷新后重试"));
        }
        String requestId = dto.getRequestId().trim();
        LotteryDrawRequest existingRequest = findDrawRequest(userId, requestId);
        if (existingRequest != null) {
            return rebuildDrawResult(userId, existingRequest);
        }

        LotteryActivity activity = resolveActivity(dto.getActivityId());
        UserInternalVO user = userInternalFeignClient.getById(userId);
        checkLotteryDrawGuard(LotteryDrawContext.resolved(userId, dto, activity, user));
        int times = dto.getTimes() == null ? 0 : dto.getTimes();
        UserLotteryPity pityLocked = ensurePityForUpdate(userId);
        int pity = pityLocked.getPityDraws() == null ? 0 : pityLocked.getPityDraws();
        int costPer = activity.getCostPointsPerDraw() == null ? 0 : activity.getCostPointsPerDraw();
        boolean useVoucher = dto.getUseVoucher() == null || Boolean.TRUE.equals(dto.getUseVoucher());
        String batchKey = requestId;

        LotteryDrawRequest drawRequest = new LotteryDrawRequest();
        drawRequest.setUserId(userId);
        drawRequest.setActivityId(activity.getId());
        drawRequest.setRequestId(requestId);
        drawRequest.setTimes(times);
        drawRequest.setBatchKey(batchKey);
        try {
            lotteryDrawRequestMapper.insert(drawRequest);
        } catch (DuplicateKeyException ex) {
            LotteryDrawRequest raced = findDrawRequest(userId, requestId);
            if (raced != null) {
                return rebuildDrawResult(userId, raced);
            }
            throw ex;
        }

        int vouchersUsed = 0;
        if (useVoucher && costPer > 0 && times > 0) {
            UserLotteryVoucher voucherWallet = ensureVoucherForUpdate(userId);
            int available = voucherWallet.getBalance() == null ? 0 : voucherWallet.getBalance();
            vouchersUsed = Math.min(available, times);
            if (vouchersUsed > 0) {
                deductVouchers(userId, vouchersUsed, activity.getId(),
                        "lottery_voucher_draw:" + userId + ":" + requestId,
                        times == 1 ? "抽奖抵扣·单抽" : "抽奖抵扣·十连");
            }
        }
        int totalCost = Math.max(0, costPer * times - vouchersUsed * costPer);

        if (totalCost > 0) {
            String costRemark = times == 1 ? "积分抽奖·单抽" : "积分抽奖·十连";
            String costIdempotencyKey = "lottery_cost:" + userId + ":" + requestId;
            pointsService.deductPoints(userId, totalCost, Constant.POINTS_SOURCE_LOTTERY_COST,
                    activity.getId(), costRemark, costIdempotencyKey);
        }

        List<LotteryDrawItemVO> results = new ArrayList<>(times);
        boolean tenHasRare = false;
        for (int i = 0; i < times; i++) {
            boolean forceGrand = pity >= Constant.LOTTERY_HARD_PITY_AFTER_MISSES;
            boolean forceRareOnly = times == 10 && i == times - 1 && !tenHasRare;
            LotteryDrawItemVO item = executeOneDraw(userId, activity.getId(), batchKey, forceGrand, forceRareOnly);
            results.add(item);
            boolean jackpotWin = Boolean.TRUE.equals(item.getJackpot());
            if (jackpotWin) {
                pity = 0;
                userLotteryPityMapper.resetPityDraws(userId);
            } else {
                pity = nextPityAfterMiss(pity);
                userLotteryPityMapper.updatePityDraws(userId, pity);
            }
            if (isSoftPityRareOutcome(item)) {
                tenHasRare = true;
            }
        }

        drawRequest.setPityAfter(pity);
        drawRequest.setVouchersUsed(vouchersUsed);
        drawRequest.setPointsCharged(totalCost);
        lotteryDrawRequestMapper.updateById(drawRequest);

        int balanceAfter = pointsService.getWallet(userId).getBalance();
        LotteryDrawResultVO resultVO = new LotteryDrawResultVO(balanceAfter, batchKey, results, pity);
        resultVO.setVouchersUsed(vouchersUsed);
        resultVO.setPointsCharged(totalCost);
        resultVO.setVoucherBalanceAfter(getVoucherBalance(userId));
        int starlightGranted = 0;
        for (LotteryDrawItemVO item : results) {
            starlightGranted += item.getStarlightGranted() == null ? 0 : item.getStarlightGranted();
        }
        resultVO.setStarlightGranted(starlightGranted);
        resultVO.setStarlightBalanceAfter(starlightService.getBalance(userId));
        List<Integer> unlocked = unlockCollectIcons(userId, activity.getId(), resolveCollectUnlockCount(times));
        resultVO.setCollectUnlockedIconIds(unlocked);
        resultVO.setCollectOwnedCount(countOwnedIcons(userId, activity.getId()));
        List<String> milestoneGranted = autoClaimReachedMilestones(userId, activity.getId());
        resultVO.setCollectMilestoneGranted(milestoneGranted);
        resultVO.setVoucherBalanceAfter(getVoucherBalance(userId));
        resultVO.setBalanceAfter(pointsService.getWallet(userId).getBalance());
        evictPublicRecentCache(activity.getId());
        return resultVO;
    }

    static int nextPityAfterMiss(int currentPity) {
        return Math.min(Constant.LOTTERY_HARD_PITY_AFTER_MISSES, Math.max(0, currentPity) + 1);
    }

    // 保底计数落独立表 user_lottery_pity
    private UserLotteryPity ensurePityForUpdate(Long userId) {
        UserLotteryPity locked = userLotteryPityMapper.selectByUserIdForUpdate(userId);
        if (locked != null) {
            return locked;
        }
        try {
            userLotteryPityMapper.insertPity(userId, 0);
        } catch (DuplicateKeyException ignored) {
            // 并发建档
        }
        locked = userLotteryPityMapper.selectByUserIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return locked;
    }

    private LotteryDrawRequest findDrawRequest(Long userId, String requestId) {
        return lotteryDrawRequestMapper.selectOne(Wrappers.lambdaQuery(LotteryDrawRequest.class)
                .eq(LotteryDrawRequest::getUserId, userId)
                .eq(LotteryDrawRequest::getRequestId, requestId)
                .ne(LotteryDrawRequest::getDeleteState, 1)
                .last("LIMIT 1"));
    }

    private LotteryDrawResultVO rebuildDrawResult(Long userId, LotteryDrawRequest request) {
        List<LotteryDrawRecord> records = lotteryDrawRecordMapper.selectByUserAndBatchKey(userId, request.getBatchKey());
        List<LotteryDrawItemVO> items = new ArrayList<>(records.size());
        for (LotteryDrawRecord rec : records) {
            items.add(toDrawItemVO(rec));
        }
        int pity = request.getPityAfter() == null ? 0 : request.getPityAfter();
        int balanceAfter = pointsService.getWallet(userId).getBalance();
        LotteryDrawResultVO vo = new LotteryDrawResultVO(balanceAfter, request.getBatchKey(), items, pity);
        vo.setVoucherBalanceAfter(getVoucherBalance(userId));
        int starlightGranted = 0;
        for (LotteryDrawItemVO item : items) {
            starlightGranted += item.getStarlightGranted() == null ? 0 : item.getStarlightGranted();
        }
        vo.setStarlightGranted(starlightGranted);
        vo.setStarlightBalanceAfter(starlightService.getBalance(userId));
        return vo;
    }

    private Map<String, Integer> loadDrawTimesByBatchKey(Long userId, List<String> batchKeys) {
        if (batchKeys == null || batchKeys.isEmpty()) {
            return Map.of();
        }
        List<LotteryDrawRequest> requests = lotteryDrawRequestMapper.selectList(
                Wrappers.lambdaQuery(LotteryDrawRequest.class)
                        .eq(LotteryDrawRequest::getUserId, userId)
                        .in(LotteryDrawRequest::getBatchKey, batchKeys)
                        .ne(LotteryDrawRequest::getDeleteState, 1));
        Map<String, Integer> result = new HashMap<>();
        for (LotteryDrawRequest request : requests) {
            if (request.getBatchKey() != null) {
                result.put(request.getBatchKey(), request.getTimes());
            }
        }
        return result;
    }

    private LotteryDrawRecordVO toDrawRecordVO(LotteryDrawRecord record, Map<String, Integer> drawTimesByBatchKey) {
        int grantPoints = record.getGrantPoints() == null ? 0 : record.getGrantPoints();
        int grantVipDays = 0;
        if (Objects.equals(record.getMysteryItemType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = record.getMysteryItemValue() == null ? 0 : record.getMysteryItemValue();
        } else if (Objects.equals(record.getPrizeType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = record.getPrizeValue() == null ? 0 : record.getPrizeValue();
        }
        String rewardDetail = buildRewardDetail(record.getMysteryItemType(), record.getMysteryItemValue(),
                grantPoints, grantVipDays, record.getPrizeType(),
                record.getPrizeValue() == null ? 0 : record.getPrizeValue());
        Integer times = drawTimesByBatchKey.get(record.getDrawBatchKey());
        LotteryDrawRecordVO vo = new LotteryDrawRecordVO();
        vo.setRecordId(record.getId());
        vo.setPrizeName(record.getPrizeName());
        vo.setRewardDetail(rewardDetail);
        vo.setMultiDraw(times != null && times > 1 ? 1 : 0);
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private LotteryDrawItemVO toDrawItemVO(LotteryDrawRecord rec) {
        int grantPoints = rec.getGrantPoints() == null ? 0 : rec.getGrantPoints();
        int grantVipDays = 0;
        if (Objects.equals(rec.getMysteryItemType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = rec.getMysteryItemValue() == null ? 0 : rec.getMysteryItemValue();
        } else if (Objects.equals(rec.getPrizeType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = rec.getPrizeValue() == null ? 0 : rec.getPrizeValue();
        }
        boolean jackpot = rec.getIsJackpot() != null && rec.getIsJackpot() == 1;
        String rewardDetail = buildRewardDetail(rec.getMysteryItemType(), rec.getMysteryItemValue(),
                grantPoints, grantVipDays, rec.getPrizeType(),
                rec.getPrizeValue() == null ? 0 : rec.getPrizeValue());
        int starlightGranted = starlightService.amountForPrize(rec.getIsJackpot(), rec.getPrizeType());
        return new LotteryDrawItemVO(
                rec.getId(),
                rec.getPrizeName(),
                rec.getPrizeType(),
                rec.getPrizeValue(),
                grantPoints,
                jackpot,
                rewardDetail,
                starlightGranted);
    }

    private void checkLotteryDrawGuard(LotteryDrawContext context) {
        LotteryDrawGuardResult result = lotteryDrawGuardChain.check(context);
        if (!result.isPassed()) {
            throw new ApplicationException(result.getErrorResult());
        }
    }

    private LotteryDrawItemVO executeOneDraw(Long userId, Long activityId, String batchKey,
                                             boolean forceGrand, boolean forceRareOnly) {
        for (int attempt = 0; attempt < MAX_STOCK_RETRY; attempt++) {
            List<LotteryPrizePoolRow> pool = lotteryActivityPrizeMapper.selectDrawablePool(activityId);
            List<LotteryPrizePoolRow> available = pool.stream()
                    .filter(this::hasStock)
                    .collect(Collectors.toList());
            if (available.isEmpty()) {
                log.error("抽奖奖池为空 activityId={}", activityId);
                throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
            }

            List<LotteryPrizePoolRow> candidate =
                    resolveCandidatePool(activityId, available, forceGrand, forceRareOnly);
            LotteryPrizePoolRow picked = weightedPick(candidate);
            boolean unlimited = picked.getStockRemaining() != null && picked.getStockRemaining() == -1;
            if (!unlimited) {
                int ok = lotteryActivityPrizeMapper.decrementStockIfPositive(picked.getActivityPrizeId());
                if (ok != 1) {
                    continue;
                }
            }
            return persistDraw(userId, activityId, picked, batchKey);
        }
        throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
    }

    private List<LotteryPrizePoolRow> resolveCandidatePool(Long activityId,
                                                           List<LotteryPrizePoolRow> available,
                                                           boolean forceGrand, boolean forceRareOnly) {
        if (forceGrand) {
            List<LotteryPrizePoolRow> jackpots = available.stream()
                    .filter(r -> r.getIsJackpot() != null && r.getIsJackpot() == 1)
                    .collect(Collectors.toList());
            if (!jackpots.isEmpty()) {
                return jackpots;
            }
            log.warn("硬保底触发但无可抽的神秘大奖档，回退全池 activityId={}", activityId);
        }
        if (forceRareOnly) {
            List<LotteryPrizePoolRow> rare = available.stream()
                    .filter(LotteryServiceImpl::isSoftPityRareRow)
                    .collect(Collectors.toList());
            if (!rare.isEmpty()) {
                return rare;
            }
            log.warn("十连软保底稀有子池为空，回退全池 activityId={}", activityId);
        }
        return available;
    }

    // Soft 保底稀有：≥「VIP 体验」语义对齐为大赏类型大奖 / 周边小奖 / VIP 天 排除谢谢、安慰券、固定积分档
    private static boolean isSoftPityRareRow(LotteryPrizePoolRow r) {
        Byte t = r.getPrizeType();
        return Objects.equals(t, Constant.LOTTERY_PRIZE_GRAND)
                || Objects.equals(t, Constant.LOTTERY_PRIZE_SMALL)
                || Objects.equals(t, Constant.LOTTERY_PRIZE_VIP_DAYS);
    }

    private static boolean isSoftPityRareOutcome(LotteryDrawItemVO item) {
        Byte t = item.getPrizeType();
        return Objects.equals(t, Constant.LOTTERY_PRIZE_GRAND)
                || Objects.equals(t, Constant.LOTTERY_PRIZE_SMALL)
                || Objects.equals(t, Constant.LOTTERY_PRIZE_VIP_DAYS);
    }

    private boolean hasStock(LotteryPrizePoolRow row) {
        int sr = row.getStockRemaining() == null ? 0 : row.getStockRemaining();
        return sr == -1 || sr > 0;
    }

    private LotteryPrizePoolRow weightedPick(List<LotteryPrizePoolRow> pool) {
        int totalWeight = 0;
        for (LotteryPrizePoolRow r : pool) {
            totalWeight += Math.max(0, r.getWeight() == null ? 0 : r.getWeight());
        }
        if (totalWeight <= 0) {
            return pool.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (LotteryPrizePoolRow r : pool) {
            int w = Math.max(0, r.getWeight() == null ? 0 : r.getWeight());
            acc += w;
            if (roll < acc) {
                return r;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private LotteryDrawItemVO persistDraw(Long userId, Long activityId, LotteryPrizePoolRow picked,
                                          String batchKey) {
        int grantPoints = 0;
        int grantVipDays = 0;
        Byte mysteryItemType = null;
        Integer mysteryItemValue = null;
        int prizeValueSnapshot = picked.getPrizeValue() == null ? 0 : picked.getPrizeValue();

        if (isMysteryGrand(picked)) {
            LotteryPrizeMysteryItem sub = pickMysteryItem(picked.getPrizeId());
            if (sub != null) {
                mysteryItemType = sub.getItemType();
                mysteryItemValue = sub.getItemValue() == null ? 0 : sub.getItemValue();
                prizeValueSnapshot = mysteryItemValue;
                if (Objects.equals(sub.getItemType(), Constant.LOTTERY_PRIZE_POINTS)) {
                    grantPoints = resolvePointsGrant(mysteryItemValue);
                    prizeValueSnapshot = grantPoints;
                } else if (Objects.equals(sub.getItemType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
                    grantVipDays = Math.max(0, mysteryItemValue);
                }
            }
        } else if (Objects.equals(picked.getPrizeType(), Constant.LOTTERY_PRIZE_POINTS)) {
            grantPoints = resolvePointsGrant(picked.getPrizeValue());
            prizeValueSnapshot = grantPoints;
        } else if (Objects.equals(picked.getPrizeType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
            grantVipDays = picked.getPrizeValue() == null ? 0 : Math.max(0, picked.getPrizeValue());
        }

        LotteryDrawRecord rec = new LotteryDrawRecord();
        rec.setUserId(userId);
        rec.setActivityId(activityId);
        rec.setActivityPrizeId(picked.getActivityPrizeId());
        rec.setPrizeId(picked.getPrizeId());
        rec.setPrizeName(picked.getPrizeName());
        rec.setPrizeType(picked.getPrizeType());
        rec.setPrizeValue(prizeValueSnapshot);
        rec.setGrantPoints(grantPoints);
        rec.setIsJackpot(picked.getIsJackpot());
        rec.setMysteryItemType(mysteryItemType);
        rec.setMysteryItemValue(mysteryItemValue);
        rec.setDrawBatchKey(batchKey);
        lotteryDrawRecordMapper.insert(rec);

        ZonedDateTime hourBucket = ZonedDateTime.now(ZONE_SH).withMinute(0).withSecond(0).withNano(0);
        Timestamp statHour = Timestamp.from(hourBucket.toInstant());
        TransactionHooks.afterCommit(() -> lotteryDrawHourlyStatMapper.incrementCount(activityId, statHour));

        if (grantPoints > 0) {
            pointsService.addPoints(userId, grantPoints, Constant.POINTS_SOURCE_LOTTERY_WIN,
                    rec.getId(), "积分抽奖·中奖", "lottery_win:" + userId + ":" + rec.getId());
        }
        if (grantVipDays > 0) {
            vipSubscribeService.grantTrialVipDays(
                    userId, grantVipDays, "LOTTERY", "LOTTERY:" + rec.getId());
        }

        boolean jackpot = picked.getIsJackpot() != null && picked.getIsJackpot() == 1;
        int starlightGranted = starlightService.amountForPrize(picked.getIsJackpot(), picked.getPrizeType());
        if (starlightGranted > 0) {
            starlightService.credit(
                    userId,
                    starlightGranted,
                    StarlightServiceImpl.SOURCE_DRAW,
                    rec.getId(),
                    "starlight_draw:" + rec.getId(),
                    "抽奖获得·" + picked.getPrizeName()
            );
        }
        String rewardDetail = buildRewardDetail(mysteryItemType, mysteryItemValue, grantPoints, grantVipDays,
                picked.getPrizeType(), prizeValueSnapshot);
        LotteryDrawItemVO itemVO = new LotteryDrawItemVO(
                rec.getId(),
                picked.getPrizeName(),
                picked.getPrizeType(),
                prizeValueSnapshot,
                grantPoints,
                jackpot,
                rewardDetail,
                starlightGranted);
        return itemVO;
    }

    private int resolvePointsGrant(Integer prizeValue) {
        if (prizeValue == null) {
            return 0;
        }
        if (prizeValue == Constant.LOTTERY_RANDOM_POINTS_MARKER || prizeValue < 0) {
            int min = Constant.LOTTERY_RANDOM_POINTS_MIN;
            int max = Constant.LOTTERY_RANDOM_POINTS_MAX;
            if (max < min) {
                return Math.max(0, min);
            }
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        return Math.max(0, prizeValue);
    }

    private String buildRewardDetail(Byte mysteryItemType, Integer mysteryItemValue, int grantPoints, int grantVipDays,
                                   Byte prizeType, int prizeValueSnapshot) {
        if (mysteryItemType != null) {
            if (Objects.equals(mysteryItemType, Constant.LOTTERY_PRIZE_POINTS)) {
                return "积分 " + Math.max(0, mysteryItemValue == null ? 0 : mysteryItemValue);
            }
            if (Objects.equals(mysteryItemType, Constant.LOTTERY_PRIZE_VIP_DAYS)) {
                return "VIP 体验 " + Math.max(0, mysteryItemValue == null ? 0 : mysteryItemValue) + " 天";
            }
            return "神秘子项奖励";
        }
        if (grantVipDays > 0) {
            return "VIP 体验 " + grantVipDays + " 天";
        }
        if (grantPoints > 0) {
            return "积分 " + grantPoints;
        }
        if (Objects.equals(prizeType, Constant.LOTTERY_PRIZE_POINTS) && prizeValueSnapshot > 0) {
            return "积分 " + prizeValueSnapshot;
        }
        if (Objects.equals(prizeType, Constant.LOTTERY_PRIZE_VIP_DAYS) && prizeValueSnapshot > 0) {
            return "VIP 体验 " + prizeValueSnapshot + " 天";
        }
        return null;
    }

    private boolean isMysteryGrand(LotteryPrizePoolRow p) {
        return Objects.equals(p.getPrizeType(), Constant.LOTTERY_PRIZE_GRAND)
                && p.getIsMysteryBundle() != null && p.getIsMysteryBundle() == 1;
    }

    private LotteryPrizeMysteryItem pickMysteryItem(Long prizeId) {
        List<LotteryPrizeMysteryItem> items = lotteryPrizeMysteryItemMapper.selectList(
                Wrappers.lambdaQuery(LotteryPrizeMysteryItem.class)
                        .eq(LotteryPrizeMysteryItem::getPrizeId, prizeId)
                        .eq(LotteryPrizeMysteryItem::getDeleteState, (byte) 0));
        if (items.isEmpty()) {
            return null;
        }
        return mysteryWeightedPick(items);
    }

    private LotteryPrizeMysteryItem mysteryWeightedPick(List<LotteryPrizeMysteryItem> pool) {
        int totalWeight = 0;
        for (LotteryPrizeMysteryItem r : pool) {
            totalWeight += Math.max(0, r.getWeight() == null ? 0 : r.getWeight());
        }
        if (totalWeight <= 0) {
            return pool.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (LotteryPrizeMysteryItem r : pool) {
            int w = Math.max(0, r.getWeight() == null ? 0 : r.getWeight());
            acc += w;
            if (roll < acc) {
                return r;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private LotteryActivity resolveActivity(Long activityId) {
        LotteryActivity activity;
        if (activityId != null && activityId > 0) {
            activity = lotteryActivityMapper.selectById(activityId);
            if (activity == null || (activity.getDeleteState() != null && activity.getDeleteState() != 0)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_LOTTERY_INACTIVE));
            }
        } else {
            activity = lotteryActivityMapper.selectActiveOne();
            if (activity == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_LOTTERY_INACTIVE));
            }
        }
        if (!isActivityValid(activity)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_LOTTERY_INACTIVE));
        }
        return activity;
    }

    private boolean isActivityValid(LotteryActivity a) {
        if (a.getPhase() == null || a.getPhase() != 1) {
            return false;
        }
        if (a.getStatus() == null || a.getStatus() != 1) {
            return false;
        }
        Date now = new Date();
        if (a.getStartTime() != null && now.before(a.getStartTime())) {
            return false;
        }
        return a.getEndTime() == null || !now.after(a.getEndTime());
    }

    @Override
    public PageResult<LotteryPublicRecentDrawVO> pagePublicRecentDraws(Long activityId, Integer pageNum, Integer pageSize) {
        LotteryActivity activity = resolveActivity(activityId);
        int validPageSize = pageSize == null || pageSize < 1
                ? DEFAULT_PUBLIC_PAGE_SIZE
                : Math.min(pageSize, DEFAULT_PUBLIC_PAGE_SIZE);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        if (validPageNum > PUBLIC_MAX_PAGES) {
            validPageNum = PUBLIC_MAX_PAGES;
        }
        List<LotteryPublicRecentDrawVO> window = loadPublicRecentWindow(activity.getId());
        long dbTotal = lotteryDrawRecordMapper.countPublicByActivity(activity.getId());
        long total = Math.min(dbTotal, PUBLIC_RECENT_WINDOW);
        long pages = total == 0 ? 0 : Math.min((total + validPageSize - 1) / validPageSize, PUBLIC_MAX_PAGES);
        if (pages > 0 && validPageNum > pages) {
            validPageNum = (int) pages;
        }
        int from = (validPageNum - 1) * validPageSize;
        List<LotteryPublicRecentDrawVO> records;
        if (from >= window.size()) {
            records = List.of();
        } else {
            int to = Math.min(from + validPageSize, window.size());
            records = window.subList(from, to);
        }
        boolean hasNext = validPageNum < pages;
        return new PageResult<>(records, total, validPageNum, validPageSize, pages, hasNext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LotteryPoolTaskVO claimPoolTask(Long userId, LotteryTaskClaimDTO dto) {
        if (userId == null || userId <= 0 || dto == null
                || dto.getTaskCode() == null || dto.getTaskCode().isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryActivity activity = resolveActivity(dto.getActivityId());
        String taskCode = dto.getTaskCode().trim();
        LotteryPoolTask task = lotteryPoolTaskMapper.selectOne(Wrappers.lambdaQuery(LotteryPoolTask.class)
                .eq(LotteryPoolTask::getActivityId, activity.getId())
                .eq(LotteryPoolTask::getTaskCode, taskCode)
                .eq(LotteryPoolTask::getEnabled, 1)
                .ne(LotteryPoolTask::getDeleteState, 1)
                .last("LIMIT 1"));
        if (task == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS, "任务不存在"));
        }
        LocalDate today = LocalDate.now(ZONE_SH);
        UserLotteryTaskClaim existing = userLotteryTaskClaimMapper.selectOneClaim(
                userId, activity.getId(), taskCode, today);
        if (existing != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "今日已领取该任务奖励"));
        }
        LotteryPoolTaskVO view = buildSingleTaskView(userId, activity.getId(), task);
        if (!Constant.LOTTERY_TASK_STATUS_CLAIMABLE.equals(view.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "任务尚未完成，无法领取"));
        }
        int reward = task.getVoucherReward() == null ? 0 : task.getVoucherReward();
        UserLotteryTaskClaim claim = new UserLotteryTaskClaim();
        claim.setUserId(userId);
        claim.setActivityId(activity.getId());
        claim.setTaskCode(taskCode);
        claim.setClaimDate(today);
        claim.setVoucherGranted(reward);
        try {
            userLotteryTaskClaimMapper.insert(claim);
        } catch (DuplicateKeyException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "今日已领取该任务奖励"));
        }
        if (reward > 0) {
            grantVouchers(userId, reward, activity.getId(),
                    "lottery_task:" + userId + ":" + activity.getId() + ":" + taskCode + ":" + today,
                    "本池任务奖励·" + task.getTitle());
        }
        view.setStatus(Constant.LOTTERY_TASK_STATUS_CLAIMED);
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LotteryCollectVO claimCollectMilestone(Long userId, LotteryCollectClaimDTO dto) {
        if (userId == null || userId <= 0 || dto == null || dto.getThresholdCount() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryActivity activity = resolveActivity(dto.getActivityId());
        int threshold = dto.getThresholdCount();
        LotteryCollectMilestone milestone = lotteryCollectMilestoneMapper.selectOne(
                Wrappers.lambdaQuery(LotteryCollectMilestone.class)
                        .eq(LotteryCollectMilestone::getThresholdCount, threshold)
                        .eq(LotteryCollectMilestone::getEnabled, 1)
                        .ne(LotteryCollectMilestone::getDeleteState, 1)
                        .last("LIMIT 1"));
        if (milestone == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS, "里程奖励不存在"));
        }
        int ownedCount = countOwnedIcons(userId, activity.getId());
        if (ownedCount < threshold) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "收集进度不足，还需 " + (threshold - ownedCount) + " 枚"));
        }
        String granted = tryGrantCollectMilestone(userId, activity.getId(), milestone);
        if (granted == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "该里程奖励已领取"));
        }
        return buildCollectView(userId, activity.getId());
    }

    private LotteryCollectVO buildCollectView(Long userId, Long activityId) {
        LotteryCollectVO vo = new LotteryCollectVO();
        vo.setTotalIcons(Constant.LOTTERY_COLLECT_TOTAL_ICONS);
        List<Integer> owned = userLotteryCollectOwnedMapper.selectOwnedIconIds(userId, activityId);
        if (owned == null) {
            owned = List.of();
        }
        vo.setOwnedIconIds(owned);
        vo.setOwnedCount(owned.size());
        List<Integer> claimed = userLotteryCollectClaimMapper.selectClaimedThresholds(userId, activityId);
        if (claimed == null) {
            claimed = List.of();
        }
        vo.setClaimedThresholds(claimed);
        List<LotteryCollectMilestone> rows = listEnabledCollectMilestones();
        List<LotteryCollectMilestoneVO> milestones = new ArrayList<>();
        for (LotteryCollectMilestone row : rows) {
            LotteryCollectMilestoneVO item = new LotteryCollectMilestoneVO();
            item.setThresholdCount(row.getThresholdCount());
            item.setRewardType(row.getRewardType());
            item.setRewardValue(row.getRewardValue());
            item.setAltRewardValue(row.getAltRewardValue());
            item.setLabel(row.getLabel());
            boolean isClaimed = claimed.contains(row.getThresholdCount());
            item.setClaimed(isClaimed);
            item.setReachable(owned.size() >= (row.getThresholdCount() == null ? 0 : row.getThresholdCount()));
            milestones.add(item);
        }
        vo.setMilestones(milestones);
        return vo;
    }

    private List<LotteryCollectMilestone> listEnabledCollectMilestones() {
        return lotteryCollectMilestoneMapper.selectList(
                Wrappers.lambdaQuery(LotteryCollectMilestone.class)
                        .eq(LotteryCollectMilestone::getEnabled, 1)
                        .ne(LotteryCollectMilestone::getDeleteState, 1)
                        .orderByAsc(LotteryCollectMilestone::getSortOrder)
                        .orderByAsc(LotteryCollectMilestone::getThresholdCount));
    }

    private int countOwnedIcons(Long userId, Long activityId) {
        Long count = userLotteryCollectOwnedMapper.selectCount(Wrappers.lambdaQuery(UserLotteryCollectOwned.class)
                .eq(UserLotteryCollectOwned::getUserId, userId)
                .eq(UserLotteryCollectOwned::getActivityId, activityId)
                .ne(UserLotteryCollectOwned::getDeleteState, 1));
        return count == null ? 0 : count.intValue();
    }

    private int resolveCollectUnlockCount(int drawTimes) {
        if (drawTimes >= 10) {
            int min = Constant.LOTTERY_COLLECT_TEN_UNLOCK_MIN;
            int max = Constant.LOTTERY_COLLECT_TEN_UNLOCK_MAX;
            if (max < min) {
                max = min;
            }
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        return Math.max(0, drawTimes);
    }

    private List<Integer> unlockCollectIcons(Long userId, Long activityId, int unlockCount) {
        int need = Math.max(0, unlockCount);
        if (need <= 0) {
            return List.of();
        }
        List<Integer> owned = userLotteryCollectOwnedMapper.selectOwnedIconIds(userId, activityId);
        java.util.HashSet<Integer> ownedSet = new java.util.HashSet<>(owned == null ? List.of() : owned);
        List<Integer> locked = new ArrayList<>();
        for (int iconId = 1; iconId <= Constant.LOTTERY_COLLECT_TOTAL_ICONS; iconId++) {
            if (!ownedSet.contains(iconId)) {
                locked.add(iconId);
            }
        }
        if (locked.isEmpty()) {
            return List.of();
        }
        List<Integer> unlocked = new ArrayList<>();
        for (int i = 0; i < need && !locked.isEmpty(); i++) {
            int idx = ThreadLocalRandom.current().nextInt(locked.size());
            Integer iconId = locked.remove(idx);
            UserLotteryCollectOwned row = new UserLotteryCollectOwned();
            row.setUserId(userId);
            row.setActivityId(activityId);
            row.setIconId(iconId);
            try {
                userLotteryCollectOwnedMapper.insert(row);
                unlocked.add(iconId);
            } catch (DuplicateKeyException ignored) {
                // 并发下已拥有则跳过
            }
        }
        return unlocked;
    }

    private List<String> autoClaimReachedMilestones(Long userId, Long activityId) {
        int ownedCount = countOwnedIcons(userId, activityId);
        if (ownedCount <= 0) {
            return List.of();
        }
        List<Integer> claimed = userLotteryCollectClaimMapper.selectClaimedThresholds(userId, activityId);
        java.util.HashSet<Integer> claimedSet = new java.util.HashSet<>(claimed == null ? List.of() : claimed);
        List<String> granted = new ArrayList<>();
        for (LotteryCollectMilestone milestone : listEnabledCollectMilestones()) {
            Integer threshold = milestone.getThresholdCount();
            if (threshold == null || ownedCount < threshold || claimedSet.contains(threshold)) {
                continue;
            }
            String label = tryGrantCollectMilestone(userId, activityId, milestone);
            if (label != null) {
                granted.add(label);
                claimedSet.add(threshold);
            }
        }
        return granted;
    }

    // 成功发放返回展示文案；已领取或失败返回 null
    private String tryGrantCollectMilestone(Long userId, Long activityId, LotteryCollectMilestone milestone) {
        if (milestone == null || milestone.getThresholdCount() == null) {
            return null;
        }
        int threshold = milestone.getThresholdCount();
        String rewardType = milestone.getRewardType() == null ? "" : milestone.getRewardType().trim().toUpperCase();
        int rewardValue = milestone.getRewardValue() == null ? 0 : Math.max(0, milestone.getRewardValue());
        String displayLabel = milestone.getLabel() == null ? ("里程" + threshold) : milestone.getLabel();
        if (Constant.LOTTERY_COLLECT_REWARD_RANDOM.equals(rewardType)) {
            boolean giveVoucher = ThreadLocalRandom.current().nextBoolean();
            if (giveVoucher) {
                rewardType = Constant.LOTTERY_COLLECT_REWARD_VOUCHER;
                rewardValue = Math.max(1, rewardValue);
                displayLabel = "抵扣券×" + rewardValue;
            } else {
                rewardType = Constant.LOTTERY_COLLECT_REWARD_POINTS;
                int alt = milestone.getAltRewardValue() == null ? 30 : Math.max(1, milestone.getAltRewardValue());
                rewardValue = alt;
                displayLabel = "积分×" + rewardValue;
            }
        }
        UserLotteryCollectClaim claim = new UserLotteryCollectClaim();
        claim.setUserId(userId);
        claim.setActivityId(activityId);
        claim.setThresholdCount(threshold);
        claim.setRewardType(rewardType);
        claim.setRewardValue(rewardValue);
        try {
            userLotteryCollectClaimMapper.insert(claim);
        } catch (DuplicateKeyException ex) {
            return null;
        }
        String idem = "lottery_collect:" + userId + ":" + activityId + ":" + threshold;
        if (Constant.LOTTERY_COLLECT_REWARD_VOUCHER.equals(rewardType) && rewardValue > 0) {
            grantVouchers(userId, rewardValue, activityId, idem,
                    "收集册里程·" + displayLabel, Constant.LOTTERY_VOUCHER_SOURCE_COLLECT);
            return displayLabel;
        }
        if (Constant.LOTTERY_COLLECT_REWARD_POINTS.equals(rewardType) && rewardValue > 0) {
            pointsService.addPoints(userId, rewardValue, Constant.POINTS_SOURCE_LOTTERY_COLLECT,
                    activityId, "收集册里程·" + displayLabel, idem);
            return displayLabel;
        }
        if (Constant.LOTTERY_COLLECT_REWARD_VIP_DAYS.equals(rewardType) && rewardValue > 0) {
            vipSubscribeService.grantTrialVipDays(userId, rewardValue, "LOTTERY_COLLECT", idem);
            return displayLabel;
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "里程奖励配置无效"));
    }

    private List<LotteryPublicRecentDrawVO> loadPublicRecentWindow(Long activityId) {
        String cacheKey = Constant.REDIS_KEY_LOTTERY_PUBLIC_RECENT + activityId + ":w" + PUBLIC_RECENT_WINDOW;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, new TypeReference<List<LotteryPublicRecentDrawVO>>() {
                });
            }
        } catch (Exception e) {
            log.warn("读取公开中奖缓存失败 activityId={}", activityId, e);
        }
        List<LotteryPublicRecentDrawVO> rows = loadPublicRecentFromDb(activityId, PUBLIC_RECENT_WINDOW);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(rows),
                    Constant.REDIS_TTL_LOTTERY_PUBLIC_RECENT, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入公开中奖缓存失败 activityId={}", activityId, e);
        }
        return rows;
    }

    private List<LotteryPublicRecentDrawVO> loadPublicRecentFromDb(Long activityId, int limit) {
        List<LotteryDrawRecord> records = lotteryDrawRecordMapper.selectRecentPublicByActivity(activityId, limit);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = records.stream()
                .map(LotteryDrawRecord::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserInternalVO> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                List<UserInternalVO> users = userInternalFeignClient.listByIds(userIds);
                if (users != null) {
                    for (UserInternalVO u : users) {
                        if (u != null && u.getId() != null) {
                            userMap.put(u.getId(), u);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("批量查询公开中奖昵称失败", e);
            }
        }
        List<LotteryPublicRecentDrawVO> rows = new ArrayList<>(records.size());
        for (LotteryDrawRecord rec : records) {
            LotteryPublicRecentDrawVO row = new LotteryPublicRecentDrawVO();
            UserInternalVO user = userMap.get(rec.getUserId());
            String nickname = user != null && user.getNickname() != null && !user.getNickname().isBlank()
                    ? user.getNickname().trim()
                    : (user != null && user.getUsername() != null ? user.getUsername() : "用户");
            String first = nickname.isEmpty() ? "用" : nickname.substring(0, 1);
            row.setNickname(first + "...");
            row.setAvatarChar(first);
            row.setPrizeName(rec.getPrizeName());
            row.setCreateTimeMillis(rec.getCreateTime() == null ? null : rec.getCreateTime().getTime());
            rows.add(row);
        }
        return rows;
    }

    private void evictPublicRecentCache(Long activityId) {
        try {
            stringRedisTemplate.delete(Constant.REDIS_KEY_LOTTERY_PUBLIC_RECENT + activityId + ":w"
                    + PUBLIC_RECENT_WINDOW);
        } catch (Exception e) {
            log.warn("清理公开中奖缓存失败 activityId={}", activityId, e);
        }
    }

    private List<LotteryPoolTaskVO> buildPoolTaskViews(Long userId, Long activityId) {
        List<LotteryPoolTask> tasks = lotteryPoolTaskMapper.selectEnabledByActivityId(activityId);
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<LotteryPoolTaskVO> views = new ArrayList<>(tasks.size());
        for (LotteryPoolTask task : tasks) {
            views.add(buildSingleTaskView(userId, activityId, task));
        }
        return views;
    }

    private LotteryPoolTaskVO buildSingleTaskView(Long userId, Long activityId, LotteryPoolTask task) {
        LotteryPoolTaskVO vo = new LotteryPoolTaskVO();
        vo.setTaskCode(task.getTaskCode());
        vo.setTitle(task.getTitle());
        vo.setTargetCount(task.getTargetCount());
        vo.setVoucherReward(task.getVoucherReward());
        int current = resolveTaskProgress(userId, task.getTaskCode());
        int target = task.getTargetCount() == null ? 1 : task.getTargetCount();
        vo.setCurrentCount(Math.min(current, target));
        LocalDate today = LocalDate.now(ZONE_SH);
        UserLotteryTaskClaim claim = userLotteryTaskClaimMapper.selectOneClaim(
                userId, activityId, task.getTaskCode(), today);
        if (claim != null) {
            vo.setStatus(Constant.LOTTERY_TASK_STATUS_CLAIMED);
        } else if (current >= target) {
            vo.setStatus(Constant.LOTTERY_TASK_STATUS_CLAIMABLE);
        } else {
            vo.setStatus(Constant.LOTTERY_TASK_STATUS_LOCKED);
        }
        return vo;
    }

    private int resolveTaskProgress(Long userId, String taskCode) {
        if (Constant.LOTTERY_TASK_CHECKIN_TODAY.equals(taskCode)) {
            UserCheckinInfo info = userCheckinInfoMapper.selectOne(Wrappers.lambdaQuery(UserCheckinInfo.class)
                    .eq(UserCheckinInfo::getUserId, userId)
                    .ne(UserCheckinInfo::getDeleteState, 1)
                    .last("LIMIT 1"));
            if (info == null || info.getLastCheckin() == null) {
                return 0;
            }
            LocalDate last = LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(info.getLastCheckin().getTime()), ZONE_SH);
            return last.equals(LocalDate.now(ZONE_SH)) ? 1 : 0;
        }
        if (Constant.LOTTERY_TASK_COMMENT_1.equals(taskCode)
                || Constant.LOTTERY_TASK_LIKE_3.equals(taskCode)) {
            try {
                UserDailyEngagementInternalVO engagement =
                        userEngagementInternalFeignClient.getDailyEngagement(userId);
                if (engagement == null) {
                    return 0;
                }
                if (Constant.LOTTERY_TASK_COMMENT_1.equals(taskCode)) {
                    return engagement.getCommentCount() == null ? 0 : engagement.getCommentCount();
                }
                return engagement.getLikeCount() == null ? 0 : engagement.getLikeCount();
            } catch (Exception e) {
                log.warn("拉取当日互动计数失败 userId={}", userId, e);
                return 0;
            }
        }
        return 0;
    }

    private int getVoucherBalance(Long userId) {
        UserLotteryVoucher wallet = userLotteryVoucherMapper.selectByUserId(userId);
        return wallet == null || wallet.getBalance() == null ? 0 : wallet.getBalance();
    }

    private UserLotteryVoucher ensureVoucherForUpdate(Long userId) {
        UserLotteryVoucher locked = userLotteryVoucherMapper.selectByUserIdForUpdate(userId);
        if (locked != null) {
            return locked;
        }
        try {
            userLotteryVoucherMapper.insertWallet(userId);
        } catch (DuplicateKeyException ignored) {
            // 并发建档
        }
        locked = userLotteryVoucherMapper.selectByUserIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return locked;
    }

    private void grantVouchers(Long userId, int amount, Long relatedId, String idempotencyKey, String remark) {
        grantVouchers(userId, amount, relatedId, idempotencyKey, remark, Constant.LOTTERY_VOUCHER_SOURCE_TASK);
    }

    private void grantVouchers(Long userId, int amount, Long relatedId, String idempotencyKey, String remark,
                               Byte sourceType) {
        if (amount <= 0) {
            return;
        }
        LotteryVoucherLog existing = lotteryVoucherLogMapper.selectOne(Wrappers.lambdaQuery(LotteryVoucherLog.class)
                .eq(LotteryVoucherLog::getUserId, userId)
                .eq(LotteryVoucherLog::getIdempotencyKey, idempotencyKey)
                .ne(LotteryVoucherLog::getDeleteState, 1)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        UserLotteryVoucher wallet = ensureVoucherForUpdate(userId);
        int before = wallet.getBalance() == null ? 0 : wallet.getBalance();
        int after = before + amount;
        int version = wallet.getVersion() == null ? 0 : wallet.getVersion();
        int affected = userLotteryVoucherMapper.updateBalanceOptimistic(userId, after, version);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "抵扣券发放失败，请重试"));
        }
        Byte source = sourceType == null ? Constant.LOTTERY_VOUCHER_SOURCE_TASK : sourceType;
        insertVoucherLog(userId, amount, after, source, relatedId, idempotencyKey, remark);
    }

    private void deductVouchers(Long userId, int amount, Long relatedId, String idempotencyKey, String remark) {
        if (amount <= 0) {
            return;
        }
        LotteryVoucherLog existing = lotteryVoucherLogMapper.selectOne(Wrappers.lambdaQuery(LotteryVoucherLog.class)
                .eq(LotteryVoucherLog::getUserId, userId)
                .eq(LotteryVoucherLog::getIdempotencyKey, idempotencyKey)
                .ne(LotteryVoucherLog::getDeleteState, 1)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        UserLotteryVoucher wallet = ensureVoucherForUpdate(userId);
        int before = wallet.getBalance() == null ? 0 : wallet.getBalance();
        if (before < amount) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "抵扣券不足"));
        }
        int after = before - amount;
        int version = wallet.getVersion() == null ? 0 : wallet.getVersion();
        int affected = userLotteryVoucherMapper.updateBalanceOptimistic(userId, after, version);
        if (affected != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "抵扣券扣减失败，请重试"));
        }
        insertVoucherLog(userId, -amount, after, Constant.LOTTERY_VOUCHER_SOURCE_DRAW, relatedId, idempotencyKey, remark);
    }

    private void insertVoucherLog(Long userId, int delta, int balanceAfter, Byte sourceType,
                                  Long relatedId, String idempotencyKey, String remark) {
        LotteryVoucherLog logRow = new LotteryVoucherLog();
        logRow.setUserId(userId);
        logRow.setDelta(delta);
        logRow.setBalanceAfter(balanceAfter);
        logRow.setSourceType(sourceType);
        logRow.setRelatedId(relatedId);
        logRow.setIdempotencyKey(idempotencyKey);
        logRow.setRemark(remark);
        try {
            lotteryVoucherLogMapper.insert(logRow);
        } catch (DuplicateKeyException ignored) {
            // 幂等命中
        }
    }
}
