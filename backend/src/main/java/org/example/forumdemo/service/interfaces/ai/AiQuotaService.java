package org.example.forumdemo.service.interfaces.ai;

import org.example.forumdemo.entity.db.User;

/**
 * AI 能力按日配额：与 VIP 档位及 ai_usage_daily 一致。
 */
public interface AiQuotaService {

    /** 有效 VIP 且为 PRO/MAX 时 DeepSeek 写作不限次（不计入日额） */
    boolean hasUnlimitedDeepseek(User user);

    void consumeDeepseekWrite(User user);

    void consumeAdvancedLlm(User user);

    void consumeImageNormal(User user);

    void consumeImagePremium(User user);

    void releaseDeepseekWrite(User user);

    void releaseAdvancedLlm(User user);

    void releaseImageNormal(User user);

    void releaseImagePremium(User user);

    /** 封面「推荐配图要点」：仅审计计数，不计入 DeepSeek 写作配额 */
    void recordCoverHint(User user);
}
