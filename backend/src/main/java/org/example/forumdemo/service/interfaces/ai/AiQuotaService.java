package org.example.forumdemo.service.interfaces.ai;

import org.example.forumdemo.entity.db.User;

/**
 * AI 能力按日配额：与 VIP 档位及 ai_usage_daily 一致。
 */
public interface AiQuotaService {

    /** 有效 VIP 且为 PRO/MAX 时可使用 Qwen 深度档。 */
    boolean hasAdvancedQwenAccess(User user);

    void consumeQwenFlash(User user);

    void consumeAdvancedLlm(User user);

    void consumeImageNormal(User user);

    void consumeImagePremium(User user);

    void releaseQwenFlash(User user);

    void releaseAdvancedLlm(User user);

    void releaseImageNormal(User user);

    void releaseImagePremium(User user);

    /** 封面「推荐配图要点」：仅审计计数，不计入文本写作配额 */
    void recordCoverHint(User user);
}
