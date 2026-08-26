package org.pluchon.forum.controller;

import org.pluchon.forum.api.ai.AiUsageDailyBucketsVO;
import org.pluchon.forum.api.ai.AiUsageInternalApi;
import org.pluchon.forum.service.internal.AiUsageInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI 用量内部接口 供 economy VIP 配额读取，避免跨域 Mapper
@RestController
public class AiUsageInternalController implements AiUsageInternalApi {

    @Autowired
    private AiUsageInternalService aiUsageInternalService;

    @Override
    public AiUsageDailyBucketsVO usageSnapshot(
            @PathVariable Long userId,
            @RequestParam("periodStartMs") long periodStartMs,
            @RequestParam("periodEndMs") long periodEndMs) {
        return aiUsageInternalService.usageSnapshot(userId, periodStartMs, periodEndMs);
    }
}
