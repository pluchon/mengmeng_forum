package org.pluchon.forum.service.impl.bag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.UserBagItem;
import org.pluchon.forum.entity.vo.bag.BagItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.UserBagItemMapper;
import org.pluchon.forum.service.interfaces.bag.UserBagService;
import org.pluchon.forum.service.interfaces.checkin.CheckinService;
import org.pluchon.forum.service.interfaces.lottery.LotteryVoucherService;
import org.pluchon.forum.service.interfaces.starlight.StarlightQuotaResetService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户背包。
 *
 * <p>兑换与中奖的「卡片类」奖品先落在这里，由用户择时使用；积分与萌星辉是流水型货币，
 * 仍旧即时到账，不进背包。实物类落库即「待发放」，没有使用入口，等管理端接手。
 */
@Slf4j
@Service
public class UserBagServiceImpl implements UserBagService {

    public static final String SOURCE_EXCHANGE = "EXCHANGE";

    public static final String SOURCE_LOTTERY = "LOTTERY";

    public static final String REWARD_LOTTERY_VOUCHER = "LOTTERY_VOUCHER";

    public static final String REWARD_MAKEUP_CARD = "MAKEUP_CARD";

    public static final String REWARD_QUOTA_RESET = "QUOTA_RESET";

    public static final String REWARD_VIP_DAYS = "VIP_DAYS";

    public static final String REWARD_GOODS = "GOODS";

    public static final int STATUS_UNUSED = 0;

    public static final int STATUS_USED = 1;

    public static final int STATUS_PENDING_DELIVERY = 2;

    private static final int DEFAULT_PAGE_SIZE = 8;

    @Autowired
    private UserBagItemMapper userBagItemMapper;

    @Autowired
    private LotteryVoucherService lotteryVoucherService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private StarlightQuotaResetService starlightQuotaResetService;

    @Override
    public void grant(Long userId, String source, Long sourceRefId, String itemName,
                      String rewardType, int rewardValue, Byte vipTier,
                      String idempotencyKey, boolean pendingDelivery) {
        if (userId == null || userId <= 0 || rewardType == null || idempotencyKey == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        UserBagItem row = new UserBagItem();
        row.setUserId(userId);
        row.setSource(source);
        row.setSourceRefId(sourceRefId);
        row.setItemName(itemName);
        row.setRewardType(rewardType);
        row.setRewardValue(Math.max(0, rewardValue));
        row.setVipTier(vipTier);
        row.setUseStatus(pendingDelivery ? STATUS_PENDING_DELIVERY : STATUS_UNUSED);
        row.setIdempotencyKey(idempotencyKey);
        row.setDeleteState((byte) 0);
        try {
            userBagItemMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 同一来源重放：背包里已经有这一件了，静默即可
            log.debug("背包物品重复发放已忽略 userId={} key={}", userId, idempotencyKey);
        }
    }

    @Override
    public PageResult<BagItemVO> list(Long userId, Integer useStatus, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int requested = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        int validPageSize = PageUtils.getValidPageSize(requested);
        Page<UserBagItem> page = new Page<>(validPageNum, validPageSize);
        Page<UserBagItem> result = userBagItemMapper.selectPage(
                page,
                Wrappers.lambdaQuery(UserBagItem.class)
                        .eq(UserBagItem::getUserId, userId)
                        .eq(UserBagItem::getDeleteState, (byte) 0)
                        .eq(useStatus != null, UserBagItem::getUseStatus, useStatus)
                        // 未使用的排前面：背包首先要回答「我还有什么能用」
                        .orderByAsc(UserBagItem::getUseStatus)
                        .orderByDesc(UserBagItem::getId)
        );
        List<BagItemVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        PageResult<BagItemVO> pr = new PageResult<>();
        pr.setRecords(records);
        pr.setTotal(result.getTotal());
        pr.setPageNum((int) result.getCurrent());
        pr.setPageSize((int) result.getSize());
        pr.setPages(result.getPages());
        pr.setHasNextPage(result.getCurrent() < result.getPages());
        return pr;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BagItemVO use(Long userId, Long bagItemId) {
        if (userId == null || userId <= 0 || bagItemId == null || bagItemId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        UserBagItem row = userBagItemMapper.selectByIdForUpdate(bagItemId);
        if (row == null || (row.getDeleteState() != null && row.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "背包物品不存在"));
        }
        if (!userId.equals(row.getUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "无权使用该物品"));
        }
        Integer status = row.getUseStatus();
        if (status != null && status == STATUS_USED) {
            // 行锁保证同一件只发一次，并发的第二次点击直接回放结果
            return toVO(row);
        }
        if (status != null && status == STATUS_PENDING_DELIVERY) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "实物奖品会由管理员安排发放"));
        }

        String rewardType = row.getRewardType() == null ? "" : row.getRewardType();
        int value = row.getRewardValue() == null ? 0 : row.getRewardValue();
        String idemKey = "bag_use:" + userId + ":" + row.getId();
        String summary;
        switch (rewardType) {
            case REWARD_LOTTERY_VOUCHER -> {
                lotteryVoucherService.credit(userId, value, row.getId(), idemKey,
                        "背包使用·" + row.getItemName(), Constant.LOTTERY_VOUCHER_SOURCE_STARLIGHT);
                summary = "抵扣券 ×" + value + " 已到账";
            }
            case REWARD_MAKEUP_CARD -> {
                checkinService.grantMakeupCards(userId, value);
                summary = "补签卡 ×" + value + " 已到账";
            }
            case REWARD_VIP_DAYS -> {
                Byte tier = row.getVipTier() == null ? Constant.VIP_TIER_PRO : row.getVipTier();
                vipSubscribeService.grantTrialVip(userId, tier, value, "BAG", "BAG:" + row.getId());
                summary = (Constant.VIP_TIER_MAX.equals(tier) ? "MAX" : "PRO") + " 会员体验 " + value + " 天已生效";
            }
            case REWARD_QUOTA_RESET -> summary = starlightQuotaResetService.applyQuotaReset(userId);
            default -> throw new ApplicationException(
                    Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "暂不支持使用该奖励类型"));
        }

        Date now = new Date();
        row.setUseStatus(STATUS_USED);
        row.setUseTime(now);
        row.setGrantSummary(summary);
        userBagItemMapper.updateById(row);
        return toVO(row);
    }

    @Override
    public int countUnused(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        Long count = userBagItemMapper.selectCount(
                Wrappers.lambdaQuery(UserBagItem.class)
                        .eq(UserBagItem::getUserId, userId)
                        .eq(UserBagItem::getDeleteState, (byte) 0)
                        .eq(UserBagItem::getUseStatus, STATUS_UNUSED)
        );
        return count == null ? 0 : count.intValue();
    }

    private BagItemVO toVO(UserBagItem row) {
        BagItemVO vo = new BagItemVO();
        vo.setId(row.getId());
        vo.setSource(row.getSource());
        vo.setItemName(row.getItemName());
        vo.setRewardType(row.getRewardType());
        vo.setRewardValue(row.getRewardValue());
        vo.setVipTier(row.getVipTier());
        vo.setUseStatus(row.getUseStatus() == null ? STATUS_UNUSED : row.getUseStatus());
        vo.setUseTime(row.getUseTime());
        vo.setGrantSummary(row.getGrantSummary());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
