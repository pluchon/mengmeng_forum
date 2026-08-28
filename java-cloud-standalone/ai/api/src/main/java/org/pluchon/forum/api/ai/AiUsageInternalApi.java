package org.pluchon.forum.api.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// AI 用量内部契约 纯 API，无 @FeignClient；供 economy VIP 配额面板
public interface AiUsageInternalApi {

    @GetMapping("/ai/internal/{userId}/usage-snapshot")
    AiUsageDailyBucketsVO usageSnapshot(@PathVariable Long userId,
                                        @RequestParam("periodStartMs") long periodStartMs,
                                        @RequestParam("periodEndMs") long periodEndMs
    );

    // 额度重置卡使用时由 economy 调用，把该周期已用量清零。
    // 周期由调用方传入而非 AI 域回查会员快照，避免 economy 事务内回环取锁。
    @PostMapping("/ai/internal/{userId}/quota-reset")
    void resetPeriodQuota(@PathVariable Long userId,
                          @RequestParam("periodStartMs") long periodStartMs,
                          @RequestParam("periodEndMs") long periodEndMs);
}
