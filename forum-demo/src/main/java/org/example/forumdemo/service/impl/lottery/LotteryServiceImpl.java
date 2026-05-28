package org.example.forumdemo.service.impl.lottery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.LotteryActivity;
import org.example.forumdemo.entity.db.LotteryDrawRecord;
import org.example.forumdemo.entity.db.LotteryPrizeMysteryItem;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.lottery.LotteryDrawDTO;
import org.example.forumdemo.entity.vo.lottery.LotteryActivityInfoVO;
import org.example.forumdemo.entity.vo.lottery.LotteryActivityListItemVO;
import org.example.forumdemo.entity.vo.lottery.LotteryDrawItemVO;
import org.example.forumdemo.entity.vo.lottery.LotteryDrawResultVO;
import org.example.forumdemo.entity.vo.lottery.LotteryPrizeHeatVO;
import org.example.forumdemo.entity.vo.lottery.LotteryPrizeLineVO;
import org.example.forumdemo.entity.vo.lottery.LotteryPrizePoolRow;
import org.example.forumdemo.entity.vo.lottery.LotteryRecentDrawVO;
import org.example.forumdemo.entity.vo.lottery.LotterySurpriseClaimVO;
import org.example.forumdemo.entity.vo.points.PointsWalletVO;
import org.example.forumdemo.mapper.LotteryActivityMapper;
import org.example.forumdemo.mapper.LotteryActivityPrizeMapper;
import org.example.forumdemo.mapper.LotteryDrawHourlyStatMapper;
import org.example.forumdemo.mapper.LotteryDrawRecordMapper;
import org.example.forumdemo.mapper.LotteryPrizeMysteryItemMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.lottery.LotteryService;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LotteryServiceImpl implements LotteryService {

    private static final int MAX_STOCK_RETRY = 64;

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Autowired
    private LotteryActivityMapper lotteryActivityMapper;

    @Autowired
    private LotteryActivityPrizeMapper lotteryActivityPrizeMapper;

    @Autowired
    private LotteryDrawRecordMapper lotteryDrawRecordMapper;

    @Autowired
    private LotteryDrawHourlyStatMapper lotteryDrawHourlyStatMapper;

    @Autowired
    private LotteryPrizeMysteryItemMapper lotteryPrizeMysteryItemMapper;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VipSubscribeService vipSubscribeService;

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
        User userRow = userMapper.selectById(userId);
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
        List<LotteryPrizeHeatVO> heat =
                lotteryDrawRecordMapper.selectHeatByActivity(activity.getId(), 14);

        LotteryActivityInfoVO vo = new LotteryActivityInfoVO();
        vo.setActivityId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setDescription(activity.getDescription());
        vo.setCostPointsPerDraw(activity.getCostPointsPerDraw());
        vo.setBalance(wallet.getBalance());
        vo.setPrizes(lines);
        vo.setPityDrawsSinceJackpot(userRow != null && userRow.getLotteryPityDraws() != null
                ? userRow.getLotteryPityDraws()
                : 0);
        vo.setHardPityThreshold(Constant.LOTTERY_HARD_PITY_AFTER_MISSES);
        vo.setRecentDraws(recent != null ? recent : List.of());
        vo.setPrizeWinHeat(heat != null ? heat : List.of());
        vo.setLotterySurpriseClaimed(userRow != null && userRow.getLotterySurpriseClaimed() != null
                && userRow.getLotterySurpriseClaimed() == 1);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LotterySurpriseClaimVO claimPageSurpriseBonus(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        User locked = userMapper.selectByIdForUpdate(userId);
        if (locked == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        Byte claimed = locked.getLotterySurpriseClaimed();
        if (claimed != null && claimed == 1) {
            LotterySurpriseClaimVO out = new LotterySurpriseClaimVO();
            out.setAlreadyClaimed(true);
            out.setGranted(false);
            out.setBalanceAfter(pointsService.getWallet(userId).getBalance());
            return out;
        }
        int amt = Constant.POINTS_LOTTERY_PAGE_SURPRISE_AMOUNT;
        int balanceAfter = pointsService.addPoints(userId, amt, Constant.POINTS_SOURCE_LOTTERY_PAGE_SURPRISE,
                null, "抽奖页彩蛋·点我看看");
        int rows = userMapper.markLotterySurpriseClaimed(userId);
        if (rows != 1) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        LotterySurpriseClaimVO out = new LotterySurpriseClaimVO();
        out.setGranted(true);
        out.setAlreadyClaimed(false);
        out.setGrantPoints(amt);
        out.setBalanceAfter(balanceAfter);
        return out;
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
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int times = dto.getTimes() == null ? 0 : dto.getTimes();
        if (times != 1 && times != 10) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_LOTTERY_TIMES_INVALID));
        }
        LotteryActivity activity = resolveActivity(dto.getActivityId());
        User lockedUser = userMapper.selectByIdForUpdate(userId);
        if (lockedUser == null || (lockedUser.getDeleteState() != null && lockedUser.getDeleteState() != 0)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        int pity = lockedUser.getLotteryPityDraws() == null ? 0 : lockedUser.getLotteryPityDraws();
        int totalCost = activity.getCostPointsPerDraw() * times;
        String costRemark = times == 1 ? "积分抽奖·单抽" : "积分抽奖·十连";
        pointsService.deductPoints(userId, totalCost, Constant.POINTS_SOURCE_LOTTERY_COST,
                activity.getId(), costRemark);
        String batchKey = times > 1 ? UUID.randomUUID().toString().replace("-", "") : null;
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
                userMapper.resetLotteryPityDraws(userId);
            } else {
                pity++;
                userMapper.incrementLotteryPityDraws(userId);
            }
            if (isSoftPityRareOutcome(item)) {
                tenHasRare = true;
            }
        }
        int balanceAfter = pointsService.getWallet(userId).getBalance();
        return new LotteryDrawResultVO(balanceAfter, batchKey, results, pity);
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
        lotteryDrawHourlyStatMapper.incrementCount(activityId, Timestamp.from(hourBucket.toInstant()));

        if (grantPoints > 0) {
            pointsService.addPoints(userId, grantPoints, Constant.POINTS_SOURCE_LOTTERY_WIN,
                    rec.getId(), "积分抽奖·中奖");
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
