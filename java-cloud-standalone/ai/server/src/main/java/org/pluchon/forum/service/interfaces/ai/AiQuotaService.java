package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.service.security.AiUserContext;

/**
 * AI 能力按日配额：与 VIP 档位及 ai_usage_daily 一致。
 */
public interface AiQuotaService {

    /** 有效 VIP 且为 PRO/MAX 时可使用 Qwen 深度档。 */
    boolean hasAdvancedQwenAccess(AiUserContext user);

    void consumeQwenFlash(AiUserContext user);

    void consumeAdvancedLlm(AiUserContext user);

    void consumeImageNormal(AiUserContext user);

    void consumeImagePremium(AiUserContext user);

    void releaseQwenFlash(AiUserContext user);

    void releaseAdvancedLlm(AiUserContext user);

    void releaseImageNormal(AiUserContext user);

    void releaseImagePremium(AiUserContext user);

    /** 封面「推荐配图要点」：仅审计计数，不计入文本写作配额 */
    void recordCoverHint(AiUserContext user);
}
