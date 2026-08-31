package org.pluchon.forum.service.impl.starlight;

import org.pluchon.forum.common.constant.ForumTimeZone;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.StarlightExchangeRecord;
import org.pluchon.forum.entity.db.StarlightShopItem;
import org.pluchon.forum.entity.dto.starlight.StarlightExchangeDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeRecordVO;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeResultVO;
import org.pluchon.forum.entity.vo.starlight.StarlightShopItemVO;
import org.pluchon.forum.mapper.StarlightExchangeRecordMapper;
import org.pluchon.forum.mapper.StarlightShopItemMapper;
import org.pluchon.forum.service.impl.bag.UserBagServiceImpl;
import org.pluchon.forum.service.interfaces.bag.UserBagService;
import org.pluchon.forum.service.interfaces.starlight.StarlightService;
import org.pluchon.forum.service.interfaces.starlight.StarlightShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 萌星辉商城：商品分页、兑换、兑换记录。兑换只扣款并把奖品塞进背包，发放统一交给 UserBagService
@Service
public class StarlightShopServiceImpl implements StarlightShopService {

    private static final ZoneId ZONE_SH = ForumTimeZone.ZONE_ID;

    private static final int DEFAULT_PAGE_SIZE = 8;

    private static final int MAX_EXCHANGE_PAGE_SIZE = 8;

    private static final int DEFAULT_EXCHANGE_PAGE_SIZE = 5;

    private static final String REWARD_LOTTERY_VOUCHER = "LOTTERY_VOUCHER";

    private static final String REWARD_MAKEUP_CARD = "MAKEUP_CARD";

    // 额度重置卡：清空当前配额周期已用量，PRO/MAX 同价不同效
    private static final String REWARD_QUOTA_RESET = "QUOTA_RESET";

    // 兑换记录只是购买流水；发放状态看背包，这里恒为「已入背包」
    private static final int USE_STATUS_DELIVERED = 1;

    private static final Set<String> VALID_CATEGORIES = Set.of("HOT", "LIMITED", "COSMETIC", "UTILITY");

    private static final Set<String> SUPPORTED_REWARDS = Set.of(
            REWARD_LOTTERY_VOUCHER, REWARD_MAKEUP_CARD, REWARD_QUOTA_RESET);


    @Autowired
    private StarlightShopItemMapper starlightShopItemMapper;

    @Autowired
    private StarlightExchangeRecordMapper starlightExchangeRecordMapper;

    @Autowired
    private StarlightService starlightService;

    @Autowired
    private UserBagService userBagService;

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

        // 限购是纯读判断，放在扣库存之前：否则限购已满的用户也要先去抢库存的行锁
        LocalDate today = LocalDate.now(ZONE_SH);
        int dailyLimit = item.getDailyLimit() == null ? 0 : item.getDailyLimit();
        if (dailyLimit > 0) {
            Date dayStart = Date.from(today.atStartOfDay(ZONE_SH).toInstant());
            Date dayEnd = Date.from(today.plusDays(1).atStartOfDay(ZONE_SH).toInstant());
            int used = starlightExchangeRecordMapper.countUserItemBetween(userId, item.getId(), dayStart, dayEnd);
            if (used >= dailyLimit) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "已达每日限购次数"));
            }
        }
        int weeklyLimit = item.getWeeklyLimit() == null ? 0 : item.getWeeklyLimit();
        if (weeklyLimit > 0) {
            LocalDate weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Date rangeStart = Date.from(weekStart.atStartOfDay(ZONE_SH).toInstant());
            Date rangeEnd = Date.from(weekStart.plusWeeks(1).atStartOfDay(ZONE_SH).toInstant());
            int used = starlightExchangeRecordMapper.countUserItemBetween(userId, item.getId(), rangeStart, rangeEnd);
            if (used >= weeklyLimit) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "已达每周限购次数"));
            }
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
        // 兑换即交付到背包，什么时候真正发放由用户在背包里决定
        record.setUseStatus(USE_STATUS_DELIVERED);
        record.setUseTime(now);
        record.setGrantSummary("已放入背包");
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

        userBagService.grant(
                userId,
                UserBagServiceImpl.SOURCE_EXCHANGE,
                record.getId(),
                item.getName(),
                rewardType,
                rewardValue,
                null,
                "bag_exch:" + record.getId(),
                false);

        return toExchangeResult(record, balanceAfter);
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
        vo.setWeeklyLimit(item.getWeeklyLimit());
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
        vo.setUseStatus(record.getUseStatus() == null ? USE_STATUS_DELIVERED : record.getUseStatus());
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
