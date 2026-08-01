package org.pluchon.forum.service.impl.lottery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.economy.client.EconomyUserInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.LotteryActivity;
import org.pluchon.forum.entity.db.LotteryDrawRecord;
import org.pluchon.forum.entity.db.LotteryDrawRequest;
import org.pluchon.forum.entity.db.LotteryPrizeMysteryItem;
import org.pluchon.forum.entity.db.UserLotteryPity;
import org.pluchon.forum.entity.dto.lottery.LotteryDrawDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityInfoVO;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityListItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawRecordVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawResultVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPrizeLineVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPrizePoolRow;
import org.pluchon.forum.entity.vo.lottery.LotteryRecentDrawVO;
import org.pluchon.forum.entity.vo.points.PointsWalletVO;
import org.pluchon.forum.mapper.LotteryActivityMapper;
import org.pluchon.forum.mapper.LotteryActivityPrizeMapper;
import org.pluchon.forum.mapper.LotteryDrawHourlyStatMapper;
import org.pluchon.forum.mapper.LotteryDrawRecordMapper;
import org.pluchon.forum.mapper.LotteryDrawRequestMapper;
import org.pluchon.forum.mapper.LotteryPrizeMysteryItemMapper;
import org.pluchon.forum.mapper.UserLotteryPityMapper;
import org.pluchon.forum.service.impl.lottery.guard.LotteryDrawContext;
import org.pluchon.forum.service.impl.lottery.guard.LotteryDrawGuardChain;
import org.pluchon.forum.service.impl.lottery.guard.LotteryDrawGuardResult;
import org.pluchon.forum.service.interfaces.lottery.LotteryService;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LotteryServiceImpl implements LotteryService {

    private static final int MAX_STOCK_RETRY = 64;

    private static final int DEFAULT_RECORD_PAGE_SIZE = 12;

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

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
    private VipSubscribeService vipSubscribeService;

    private LotteryDrawGuardChain lotteryDrawGuardChain = LotteryDrawGuardChain.defaultChain();

    @Autowired(required = false)
    public void setLotteryDrawGuardChain(LotteryDrawGuardChain lotteryDrawGuardChain) {
        if (lotteryDrawGuardChain != null) {
            this.lotteryDrawGuardChain = lotteryDrawGuardChain;
        }
    }

    @Override
    public List<LotteryActivityListItemVO> listSelectableActivities() {
        List<LotteryActivity> rows = lotteryActivityMapper.selectActiveList();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(this::isActivityValid)
                .map(a -> new LotteryActivityListItemVO(
                        a.getId(),
                        a.getTitle(),
                        a.getCoverImageUrl(),
                        a.getCostPointsPerDraw()))
                .collect(Collectors.toList());
    }

    @Override
    public LotteryActivityInfoVO getActivityInfo(Long userId, Long activityId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        LotteryActivity activity = resolveActivity(activityId);
        PointsWalletVO wallet = pointsService.getWallet(userId);
        List<LotteryPrizePoolRow> rows = lotteryActivityPrizeMapper.selectDrawablePool(activity.getId());
        List<LotteryPrizeLineVO> lines = rows.stream()
                .map(r -> new LotteryPrizeLineVO(
                        r.getPrizeName(),
                        r.getPrizeType(),
                        r.getPrizeValue(),
                        r.getStockRemaining(),
                        r.getIsJackpot() != null && r.getIsJackpot() == 1,
                        r.getWeight()))
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
        return vo;
    }

    @Override
    public PageResult<LotteryDrawRecordVO> queryDrawRecords(Long userId, Long activityId,
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
                        .orderByDesc(LotteryDrawRecord::getId));
        List<LotteryDrawRecordVO> records = buildDrawRecordRows(userId, result.getRecords());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    /**
     * 抽奖主流程：
     * <ul>
     *   <li>加权随机：在售库存档位上按 weight 求和抽签（含二分等价线性扫描）。</li>
     *   <li>硬保底：{@link Constant#LOTTERY_HARD_PITY_AFTER_MISSES} 次未中神秘大奖父档(is_jackpot)后，
     *       下一次强制仅在该档位抽签（售罄则回落全池并打日志）。计数持久化 user.lottery_pity_draws。</li>
     *   <li>软保底（十连）：若前 9 抽均未命中「稀有」奖品类型（大奖/小奖/VIP天），则第 10 抽仅在稀有子池中抽签；
     *       若稀有子池售罄则回落全池。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LotteryDrawResultVO draw(Long userId, LotteryDrawDTO dto) {
        checkLotteryDrawGuard(LotteryDrawContext.requestOnly(userId, dto));
        if (dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "requestId 不能为空"));
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
        int totalCost = activity.getCostPointsPerDraw() * times;
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

        String costRemark = times == 1 ? "积分抽奖·单抽" : "积分抽奖·十连";
        String costIdempotencyKey = "lottery_cost:" + userId + ":" + requestId;
        pointsService.deductPoints(userId, totalCost, Constant.POINTS_SOURCE_LOTTERY_COST,
                activity.getId(), costRemark, costIdempotencyKey);

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
        lotteryDrawRequestMapper.updateById(drawRequest);

        int balanceAfter = pointsService.getWallet(userId).getBalance();
        return new LotteryDrawResultVO(balanceAfter, batchKey, results, pity);
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
        return new LotteryDrawResultVO(balanceAfter, request.getBatchKey(), items, pity);
    }

    private List<LotteryDrawRecordVO> buildDrawRecordRows(Long userId, List<LotteryDrawRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<String> batchKeys = records.stream()
                .map(LotteryDrawRecord::getDrawBatchKey)
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Integer> drawTimesByBatchKey = loadDrawTimesByBatchKey(userId, batchKeys);
        List<LotteryDrawRecordVO> rows = new ArrayList<>(records.size());
        for (LotteryDrawRecord record : records) {
            rows.add(toDrawRecordVO(record, drawTimesByBatchKey));
        }
        return rows;
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
        return new LotteryDrawItemVO(
                rec.getId(),
                rec.getPrizeName(),
                rec.getPrizeType(),
                rec.getPrizeValue(),
                grantPoints,
                jackpot,
                rewardDetail);
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

    /** Soft 保底稀有：≥「VIP 体验」语义对齐为大赏类型大奖 / 周边小奖 / VIP 天（排除谢谢、安慰券、固定积分档）。 */
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
                    grantPoints = Math.max(0, mysteryItemValue);
                } else if (Objects.equals(sub.getItemType(), Constant.LOTTERY_PRIZE_VIP_DAYS)) {
                    grantVipDays = Math.max(0, mysteryItemValue);
                }
            }
        } else if (Objects.equals(picked.getPrizeType(), Constant.LOTTERY_PRIZE_POINTS)) {
            grantPoints = picked.getPrizeValue() == null ? 0 : Math.max(0, picked.getPrizeValue());
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
            vipSubscribeService.grantTrialVipDays(userId, grantVipDays);
        }

        boolean jackpot = picked.getIsJackpot() != null && picked.getIsJackpot() == 1;
        String rewardDetail = buildRewardDetail(mysteryItemType, mysteryItemValue, grantPoints, grantVipDays,
                picked.getPrizeType(), prizeValueSnapshot);
        return new LotteryDrawItemVO(
                rec.getId(),
                picked.getPrizeName(),
                picked.getPrizeType(),
                prizeValueSnapshot,
                grantPoints,
                jackpot,
                rewardDetail);
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
}
