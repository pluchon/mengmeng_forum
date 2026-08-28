package org.pluchon.forum.service.impl.starlight;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.cloud.feign.AiUsageInternalFeignClient;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.StarlightExchangeRecord;
import org.pluchon.forum.entity.db.StarlightShopItem;
import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.dto.starlight.StarlightExchangeDTO;
import org.pluchon.forum.entity.dto.starlight.StarlightUseDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeRecordVO;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeResultVO;
import org.pluchon.forum.entity.vo.starlight.StarlightShopItemVO;
import org.pluchon.forum.entity.vo.starlight.StarlightUseResultVO;
import org.pluchon.forum.mapper.StarlightExchangeRecordMapper;
import org.pluchon.forum.mapper.StarlightShopItemMapper;
import org.pluchon.forum.service.interfaces.checkin.CheckinService;
import org.pluchon.forum.service.interfaces.lottery.LotteryVoucherService;
import org.pluchon.forum.service.interfaces.starlight.StarlightService;
import org.pluchon.forum.service.interfaces.starlight.StarlightShopService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 萌星辉商城：商品分页、兑换入背包、背包使用发 VIP、抵扣券/补签卡兑换即时到账、兑换记录
@Service
public class StarlightShopServiceImpl implements StarlightShopService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Taipei");

    private static final int DEFAULT_PAGE_SIZE = 8;

    private static final int MAX_EXCHANGE_PAGE_SIZE = 8;

    private static final int DEFAULT_EXCHANGE_PAGE_SIZE = 5;

    private static final String REWARD_LOTTERY_VOUCHER = "LOTTERY_VOUCHER";

    private static final String REWARD_MAKEUP_CARD = "MAKEUP_CARD";

    // 额度重置卡：清空当前配额周期已用量，PRO/MAX 同价不同效
    private static final String REWARD_QUOTA_RESET = "QUOTA_RESET";

    private static final int USE_STATUS_UNUSED = 0;

    private static final int USE_STATUS_USED = 1;

    private static final Set<String> VALID_CATEGORIES = Set.of("HOT", "LIMITED", "COSMETIC", "UTILITY");

    private static final Set<String> SUPPORTED_REWARDS = Set.of(
            REWARD_LOTTERY_VOUCHER, REWARD_MAKEUP_CARD, REWARD_QUOTA_RESET);

    // 兑换后入背包、需用户主动点「使用」才生效的类型
    private static final Set<String> USABLE_REWARDS = Set.of(REWARD_QUOTA_RESET);

    @Autowired
    private StarlightShopItemMapper starlightShopItemMapper;

    @Autowired
    private StarlightExchangeRecordMapper starlightExchangeRecordMapper;

    @Autowired
    private StarlightService starlightService;

    @Autowired
    private LotteryVoucherService lotteryVoucherService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private VipEntitlementService vipEntitlementService;

    @Autowired
    private AiUsageInternalFeignClient aiUsageInternalFeignClient;

    @Override
    public PageResult<StarlightShopItemVO> pageItems(String category, Integer pageNum, Integer pageSize) {
        String cat = normalizeCategory(category);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requested = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requested);

        Page<StarlightShopItem> page = new Page<>(validPageNum, validPageSize);
        Page<StarlightShopItem> result = starlightShopItemMapper.selectPage(
                page,
                Wrappers.lambdaQuery(StarlightShopItem.class)
                        .eq(StarlightShopItem::getEnabled, 1)
                        .eq(StarlightShopItem::getCategory, cat)
                        .orderByAsc(StarlightShopItem::getSortOrder)
                        .orderByAsc(StarlightShopItem::getId)
        );
        List<StarlightShopItemVO> records = result.getRecords().stream()
                .map(this::toItemVO)
                .collect(Collectors.toList());
        return toPageResult(records, result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StarlightExchangeResultVO exchange(Long userId, StarlightExchangeDTO dto) {
        if (userId == null || userId <= 0 || dto == null || dto.getItemId() == null || dto.getItemId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String requestId = dto.getRequestId() == null ? "" : dto.getRequestId().trim();
        if (requestId.isEmpty() || requestId.length() > 64) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请求无效，请刷新后重试"));
        }
        String idemKey = "starlight_exch:" + userId + ":" + requestId;

        StarlightExchangeRecord existing = starlightExchangeRecordMapper.selectOne(
                Wrappers.lambdaQuery(StarlightExchangeRecord.class)
                        .eq(StarlightExchangeRecord::getUserId, userId)
                        .eq(StarlightExchangeRecord::getIdempotencyKey, idemKey)
        );
        if (existing != null) {
            return toExchangeResult(existing, starlightService.getBalance(userId));
        }

        StarlightShopItem item = starlightShopItemMapper.selectByIdForUpdate(dto.getItemId());
        if (item == null || item.getEnabled() == null || item.getEnabled() != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "商品不存在或已下架"));
        }
        String rewardType = item.getRewardType() == null ? "" : item.getRewardType().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_REWARDS.contains(rewardType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "暂不支持该商品类型"));
        }
        int price = item.getPriceStarlight() == null ? 0 : item.getPriceStarlight();
        int rewardValue = item.getRewardValue() == null ? 0 : item.getRewardValue();
        if (price <= 0 || rewardValue <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "商品配置无效"));
        }

        int stock = item.getStockRemaining() == null ? -1 : item.getStockRemaining();
        if (stock == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "商品已售罄"));
        }
        if (stock > 0) {
            int deducted = starlightShopItemMapper.deductStockOne(item.getId());
            if (deducted != 1) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "商品已售罄"));
            }
        }

        int dailyLimit = item.getDailyLimit() == null ? 0 : item.getDailyLimit();
        if (dailyLimit > 0) {
            LocalDate today = LocalDate.now(ZONE_SH);
            Date dayStart = Date.from(today.atStartOfDay(ZONE_SH).toInstant());
            Date dayEnd = Date.from(today.plusDays(1).atStartOfDay(ZONE_SH).toInstant());
            int used = starlightExchangeRecordMapper.countUserItemBetween(userId, item.getId(), dayStart, dayEnd);
            if (used >= dailyLimit) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "已达每日限购次数"));
            }
        }

        int balanceAfter = starlightService.debit(
                userId,
                price,
                StarlightServiceImpl.SOURCE_EXCHANGE,
                item.getId(),
                idemKey,
                "萌星辉兑换·" + item.getName()
        );

        Date now = new Date();
        StarlightExchangeRecord record = new StarlightExchangeRecord();
        record.setUserId(userId);
        record.setItemId(item.getId());
        record.setItemName(item.getName());
        record.setPricePaid(price);
        record.setRewardType(rewardType);
        record.setRewardValue(rewardValue);
        record.setIdempotencyKey(idemKey);
        if (REWARD_LOTTERY_VOUCHER.equals(rewardType)) {
            // 抵扣券兑换即时入账，无需再点「使用」
            record.setUseStatus(USE_STATUS_USED);
            record.setUseTime(now);
            record.setGrantSummary("抵扣券 ×" + rewardValue + " 已到账");
        } else if (REWARD_MAKEUP_CARD.equals(rewardType)) {
            // 补签卡兑换即时入账签到钱包
            record.setUseStatus(USE_STATUS_USED);
            record.setUseTime(now);
            record.setGrantSummary("补签卡 ×" + rewardValue + " 已到账");
        } else {
            record.setUseStatus(USE_STATUS_UNUSED);
        }
        try {
            starlightExchangeRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            return toExchangeResult(
                    starlightExchangeRecordMapper.selectOne(
                            Wrappers.lambdaQuery(StarlightExchangeRecord.class)
                                    .eq(StarlightExchangeRecord::getUserId, userId)
                                    .eq(StarlightExchangeRecord::getIdempotencyKey, idemKey)
                    ),
                    starlightService.getBalance(userId)
            );
        }

        if (REWARD_LOTTERY_VOUCHER.equals(rewardType)) {
            lotteryVoucherService.credit(
                    userId,
                    rewardValue,
                    item.getId(),
                    idemKey,
                    "萌星辉兑换·" + item.getName(),
                    Constant.LOTTERY_VOUCHER_SOURCE_STARLIGHT
            );
        } else if (REWARD_MAKEUP_CARD.equals(rewardType)) {
            checkinService.grantMakeupCards(userId, rewardValue);
        }

        // 体验卡与额度重置卡只入背包，在「使用」时发放
        return toExchangeResult(record, balanceAfter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StarlightUseResultVO use(Long userId, StarlightUseDTO dto) {
        if (userId == null || userId <= 0 || dto == null || dto.getExchangeId() == null || dto.getExchangeId() <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }

        StarlightExchangeRecord record = starlightExchangeRecordMapper.selectByIdForUpdate(dto.getExchangeId());
        if (record == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "背包物品不存在"));
        }
        if (!userId.equals(record.getUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "无权使用该物品"));
        }

        Integer status = record.getUseStatus();
        if (status != null && status == USE_STATUS_USED) {
            return toUseResult(record);
        }

        String rewardType = record.getRewardType();
        if (!USABLE_REWARDS.contains(rewardType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "暂不支持使用该奖励类型"));
        }
        Date now = new Date();
        int updated = starlightExchangeRecordMapper.markUsed(record.getId(), userId, now);
        if (updated != 1) {
            StarlightExchangeRecord latest = starlightExchangeRecordMapper.selectById(record.getId());
            if (latest != null && latest.getUseStatus() != null && latest.getUseStatus() == USE_STATUS_USED) {
                return toUseResult(latest);
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "使用失败，请刷新后重试"));
        }

        applyQuotaReset(userId, record);
        record.setUseStatus(USE_STATUS_USED);
        record.setUseTime(now);
        starlightExchangeRecordMapper.updateById(record);
        return toUseResult(record);
    }

    // 额度重置卡：把当前配额周期的已用量清零，重置到用户自己档位的上限。
    // 周期由 economy 侧算好后传给 AI 域，避免 AI 回查会员快照造成事务内回环取锁。
    private void applyQuotaReset(Long userId, StarlightExchangeRecord record) {
        UserVipSubscription subscription = vipEntitlementService.ensureCurrentBaseQuotaPeriod(userId);
        if (subscription == null
                || subscription.getQuotaPeriodStart() == null
                || subscription.getQuotaPeriodEnd() == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        Date periodEnd = subscription.getQuotaPeriodEnd();
        aiUsageInternalFeignClient.resetPeriodQuota(
                userId,
                subscription.getQuotaPeriodStart().getTime(),
                periodEnd.getTime());
        Byte tier = subscription.getVipTier() == null ? Constant.VIP_TIER_FREE : subscription.getVipTier();
        String tierLabel = Constant.VIP_TIER_MAX.equals(tier) ? "MAX"
                : (Constant.VIP_TIER_PRO.equals(tier) ? "PRO" : "免费");
        record.setActualGrantTier(tier);
        record.setGrantSummary("已重置为" + tierLabel + "档本周期额度，有效至 " + formatExpireText(periodEnd));
    }

    @Override
    public PageResult<StarlightExchangeRecordVO> pageExchanges(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requested = pageSize == null || pageSize < 1 ? DEFAULT_EXCHANGE_PAGE_SIZE : pageSize;
        int validPageSize = Math.min(MAX_EXCHANGE_PAGE_SIZE, PageUtils.getValidPageSize(requested));
        Page<StarlightExchangeRecord> page = new Page<>(validPageNum, validPageSize);
        Page<StarlightExchangeRecord> result = starlightExchangeRecordMapper.selectPage(
                page,
                Wrappers.lambdaQuery(StarlightExchangeRecord.class)
                        .eq(StarlightExchangeRecord::getUserId, userId)
                        .orderByDesc(StarlightExchangeRecord::getId)
        );

        Map<Long, String> tagByItemId = loadItemTags(result.getRecords());
        List<StarlightExchangeRecordVO> records = result.getRecords().stream()
                .map(r -> toRecordVO(r, tagByItemId.get(r.getItemId())))
                .collect(Collectors.toList());
        return toPageResult(records, result);
    }

    private Map<Long, String> loadItemTags(List<StarlightExchangeRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> itemIds = records.stream()
                .map(StarlightExchangeRecord::getItemId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<StarlightShopItem> items = starlightShopItemMapper.selectList(
                Wrappers.lambdaQuery(StarlightShopItem.class)
                        .in(StarlightShopItem::getId, itemIds)
        );
        Map<Long, String> map = new HashMap<>();
        for (StarlightShopItem item : items) {
            if (item.getId() != null) {
                map.put(item.getId(), item.getTag());
            }
        }
        return map;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "HOT";
        }
        String cat = category.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CATEGORIES.contains(cat)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "无效分类"));
        }
        return cat;
    }

    private StarlightShopItemVO toItemVO(StarlightShopItem item) {
        StarlightShopItemVO vo = new StarlightShopItemVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        vo.setCategory(item.getCategory());
        vo.setTag(item.getTag());
        vo.setPriceStarlight(item.getPriceStarlight());
        vo.setRewardType(item.getRewardType());
        vo.setRewardValue(item.getRewardValue());
        vo.setStockRemaining(item.getStockRemaining());
        vo.setDailyLimit(item.getDailyLimit());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }

    private StarlightExchangeRecordVO toRecordVO(StarlightExchangeRecord record, String tag) {
        StarlightExchangeRecordVO vo = new StarlightExchangeRecordVO();
        vo.setId(record.getId());
        vo.setItemId(record.getItemId());
        vo.setItemName(record.getItemName());
        vo.setPricePaid(record.getPricePaid());
        vo.setRewardType(record.getRewardType());
        vo.setRewardValue(record.getRewardValue());
        vo.setUseStatus(record.getUseStatus() == null ? USE_STATUS_UNUSED : record.getUseStatus());
        vo.setUseTime(record.getUseTime());
        vo.setActualGrantTier(record.getActualGrantTier());
        vo.setActualDurationHours(record.getActualDurationHours());
        if (record.getGrantSummary() != null && !record.getGrantSummary().isBlank()) {
            vo.setRewardSummary(record.getGrantSummary());
        } else {
            vo.setRewardSummary(buildRewardSummary(record.getRewardType(), record.getRewardValue()));
        }
        vo.setTag(tag);
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private static String formatExpireText(Date expireAt) {
        if (expireAt == null) {
            return "—";
        }
        var local = expireAt.toInstant().atZone(ZONE_SH).toLocalDateTime();
        return String.format("%04d-%02d-%02d %02d:%02d",
                local.getYear(), local.getMonthValue(), local.getDayOfMonth(),
                local.getHour(), local.getMinute());
    }

    private StarlightExchangeResultVO toExchangeResult(StarlightExchangeRecord record, int balanceAfter) {
        if (record == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "兑换记录不存在"));
        }
        StarlightExchangeResultVO vo = new StarlightExchangeResultVO();
        vo.setExchangeId(record.getId());
        vo.setItemName(record.getItemName());
        vo.setPricePaid(record.getPricePaid());
        vo.setRewardType(record.getRewardType());
        vo.setRewardValue(record.getRewardValue());
        vo.setStarlightBalanceAfter(balanceAfter);
        if (record.getGrantSummary() != null && !record.getGrantSummary().isBlank()) {
            vo.setRewardSummary(record.getGrantSummary());
        } else {
            vo.setRewardSummary(buildRewardSummary(record.getRewardType(), record.getRewardValue()));
        }
        return vo;
    }

    private StarlightUseResultVO toUseResult(StarlightExchangeRecord record) {
        StarlightUseResultVO vo = new StarlightUseResultVO();
        vo.setExchangeId(record.getId());
        vo.setItemName(record.getItemName());
        vo.setRewardType(record.getRewardType());
        vo.setRewardValue(record.getRewardValue());
        String summary = record.getGrantSummary();
        if (summary == null || summary.isBlank() || summary.contains("额度礼包")) {
            summary = buildRewardSummary(record.getRewardType(), record.getRewardValue());
        }
        vo.setRewardSummary(summary);
        vo.setActualGrantTier(record.getActualGrantTier());
        vo.setActualDurationHours(record.getActualDurationHours());
        vo.setUseStatus(record.getUseStatus() == null ? USE_STATUS_USED : record.getUseStatus());
        return vo;
    }

    private String buildRewardSummary(String rewardType, Integer rewardValue) {
        if (REWARD_LOTTERY_VOUCHER.equals(rewardType)) {
            return "抵扣券 ×" + Math.max(0, rewardValue == null ? 0 : rewardValue);
        }
        if (REWARD_MAKEUP_CARD.equals(rewardType)) {
            return "补签卡 ×" + Math.max(0, rewardValue == null ? 0 : rewardValue);
        }
        if (REWARD_QUOTA_RESET.equals(rewardType)) {
            return "AI 额度重置卡";
        }
        return "兑换成功";
    }

    private <T> PageResult<T> toPageResult(List<T> records, Page<?> page) {
        PageResult<T> pr = new PageResult<>();
        pr.setRecords(records);
        pr.setTotal(page.getTotal());
        pr.setPageNum((int) page.getCurrent());
        pr.setPageSize((int) page.getSize());
        pr.setPages(page.getPages());
        pr.setHasNextPage(page.getCurrent() < page.getPages());
        return pr;
    }
}
