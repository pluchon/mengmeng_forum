package org.example.forumdemo.service.impl.ai;

import jakarta.annotation.Resource;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.mapper.AiUsageDailyMapper;
import org.example.forumdemo.service.interfaces.ai.AiQuotaService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Service
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 普通用户每日 DeepSeek 写作上限 */
    private static final int FREE_DEEPSEEK_DAILY_CAP = 10;
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
    private boolean vipActive(User u) {
        Byte tier = u.getVipTier();
        if (tier == null || tier == 0) {
            return false;
        }
        Date exp = u.getVipExpireAt();
        if (exp == null) {
            return true;
        }
        return exp.after(new Date());
    }

    private boolean isProOrMax(User u) {
        return vipActive(u) && (Constant.VIP_TIER_PRO.equals(u.getVipTier())
                || Constant.VIP_TIER_MAX.equals(u.getVipTier()));
    }

    private boolean isMax(User u) {
        return vipActive(u) && Constant.VIP_TIER_MAX.equals(u.getVipTier());
    }

    private void ensureRow(Long userId, LocalDate d) {
        aiUsageDailyMapper.ensureUsageRow(userId, d);
    }

    @Override
    public boolean hasUnlimitedDeepseek(User user) {
        return isProOrMax(user);
    }

    @Override
    public void consumeDeepseekWrite(User user) {
        if (isProOrMax(user)) {
            return;
        }
        LocalDate d = today();
        ensureRow(user.getId(), d);
        int n = aiUsageDailyMapper.incrementDeepseekIfBelow(user.getId(), d, FREE_DEEPSEEK_DAILY_CAP);
        if (n != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_QUOTA_EXCEEDED));
        }
    }

    @Override
    public void consumeAdvancedLlm(User user) {
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
    public void consumeImageNormal(User user) {
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
    public void consumeImagePremium(User user) {
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
    public void recordCoverHint(User user) {
        LocalDate d = today();
        ensureRow(user.getId(), d);
        aiUsageDailyMapper.incrementCoverHint(user.getId(), d);
    }

    @Override
    public void releaseDeepseekWrite(User user) {
        if (isProOrMax(user)) {
            return;
        }
        aiUsageDailyMapper.decrementDeepseek(user.getId(), today());
    }

    @Override
    public void releaseAdvancedLlm(User user) {
        aiUsageDailyMapper.decrementAdvanced(user.getId(), today());
    }

    @Override
    public void releaseImageNormal(User user) {
        aiUsageDailyMapper.decrementImageNormal(user.getId(), today());
    }

    @Override
    public void releaseImagePremium(User user) {
        aiUsageDailyMapper.decrementImagePremium(user.getId(), today());
    }
}
