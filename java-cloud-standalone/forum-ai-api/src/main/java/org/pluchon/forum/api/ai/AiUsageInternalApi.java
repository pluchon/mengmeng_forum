package org.pluchon.forum.api.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// AI 用量内部契约（纯 API，无 @FeignClient；供 economy VIP 配额面板）
public interface AiUsageInternalApi {

    @GetMapping("/ai/internal/{userId}/usage-snapshot")
    AiUsageDailyBucketsVO usageSnapshot(
            @PathVariable("userId") Long userId,
            @RequestParam("periodStartMs") long periodStartMs,
            @RequestParam("periodEndMs") long periodEndMs
    );
}
