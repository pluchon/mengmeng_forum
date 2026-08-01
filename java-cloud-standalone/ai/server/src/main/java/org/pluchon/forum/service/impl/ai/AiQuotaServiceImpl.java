package org.pluchon.forum.service.impl.ai;

import jakarta.annotation.Resource;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.mapper.AiUsageDailyMapper;
import org.pluchon.forum.service.interfaces.ai.AiQuotaService;
import org.pluchon.forum.service.security.AiUserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Service
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 普通用户每日 Qwen Flash 写作上限 */
    private static final int FREE_QWEN_FLASH_DAILY_CAP = 10;
    /** PRO / MAX 高级模型每日上限 */
    private static final int PRO_ADVANCED_CAP = 50;
    private static final int MAX_ADVANCED_CAP = 300;
    /** 生图：普通档 / 高级档 每日上限 */
    private static final int PRO_IMAGE_NORMAL_CAP = 15;
    private static final int PRO_IMAGE_PREMIUM_CAP = 10;
    private static final int MAX_IMAGE_NORMAL_CAP = 50;
    private static final int MAX_IMAGE_PREMIUM_CAP = 50;

    @Resource
    private AiUsageDailyMapper aiUsageDailyMapper;

    private LocalDate today() {
        return LocalDate.now(SHANGHAI);
    }

    /**
     * VIP 有效：tier&gt;0 且（未填到期时间视为运营不限期，或到期时间晚于当前）
     */
    private boolean vipActive(AiUserContext user) {
        return user != null && user.isVipActive();
    }

    private boolean isProOrMax(AiUserContext user) {
        return vipActive(user) && (Constant.VIP_TIER_PRO.equals(user.getVipTier())
                || Constant.VIP_TIER_MAX.equals(user.getVipTier()));
    }

    private boolean isMax(AiUserContext user) {
        return vipActive(user) && Constant.VIP_TIER_MAX.equals(user.getVipTier());
    }

    private void ensureRow(Long userId, LocalDate d) {
        aiUsageDailyMapper.ensureUsageRow(userId, d);
    }

    @Override
    public boolean hasAdvancedQwenAccess(AiUserContext user) {
        return isProOrMax(user);
    }

    @Override
    public void consumeQwenFlash(AiUserContext user) {
        if (isProOrMax(user)) {
            return;
        }
        LocalDate d = today();
        ensureRow(user.getId(), d);
        int n = aiUsageDailyMapper.incrementQwenFlashIfBelow(user.getId(), d, FREE_QWEN_FLASH_DAILY_CAP);
        if (n != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }

    @Override
    public void consumeAdvancedLlm(AiUserContext user) {
        if (!isProOrMax(user)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        int cap = isMax(user) ? MAX_ADVANCED_CAP : PRO_ADVANCED_CAP;
        LocalDate d = today();
        ensureRow(user.getId(), d);
        int n = aiUsageDailyMapper.incrementAdvancedIfBelow(user.getId(), d, cap);
        if (n != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }

    @Override
    public void consumeImageNormal(AiUserContext user) {
        if (!isProOrMax(user)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        int cap = isMax(user) ? MAX_IMAGE_NORMAL_CAP : PRO_IMAGE_NORMAL_CAP;
        LocalDate d = today();
        ensureRow(user.getId(), d);
        int n = aiUsageDailyMapper.incrementImageNormalIfBelow(user.getId(), d, cap);
        if (n != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }

    @Override
    public void consumeImagePremium(AiUserContext user) {
        if (!isProOrMax(user)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        int cap = isMax(user) ? MAX_IMAGE_PREMIUM_CAP : PRO_IMAGE_PREMIUM_CAP;
        LocalDate d = today();
        ensureRow(user.getId(), d);
        int n = aiUsageDailyMapper.incrementImagePremiumIfBelow(user.getId(), d, cap);
        if (n != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }

    @Override
    public void recordCoverHint(AiUserContext user) {
        LocalDate d = today();
        ensureRow(user.getId(), d);
        aiUsageDailyMapper.incrementCoverHint(user.getId(), d);
    }

    @Override
    public void releaseQwenFlash(AiUserContext user) {
        if (isProOrMax(user)) {
            return;
        }
        aiUsageDailyMapper.decrementQwenFlash(user.getId(), today());
    }

    @Override
    public void releaseAdvancedLlm(AiUserContext user) {
        aiUsageDailyMapper.decrementAdvanced(user.getId(), today());
    }

    @Override
    public void releaseImageNormal(AiUserContext user) {
        aiUsageDailyMapper.decrementImageNormal(user.getId(), today());
    }

    @Override
    public void releaseImagePremium(AiUserContext user) {
        aiUsageDailyMapper.decrementImagePremium(user.getId(), today());
    }
}
