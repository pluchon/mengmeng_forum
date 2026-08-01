package org.example.forumdemo.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forum.api.ai.AiUsageDailyBucketsVO;
import org.example.forum.api.ai.AiUsageInternalApi;
import org.example.forumdemo.entity.db.AiUsageDaily;
import org.example.forumdemo.mapper.AiUsageDailyMapper;
import org.example.forumdemo.mapper.ForumAiUsageLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// AI 用量内部接口（供 economy VIP 配额读取，避免跨域 Mapper）
@RestController
public class AiUsageInternalController implements AiUsageInternalApi {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AiUsageDailyMapper aiUsageDailyMapper;

    @Autowired
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    @Override
    public AiUsageDailyBucketsVO usageSnapshot(
            @PathVariable("userId") Long userId,
            @RequestParam("periodStartMs") long periodStartMs,
            @RequestParam("periodEndMs") long periodEndMs) {
        AiUsageDailyBucketsVO vo = new AiUsageDailyBucketsVO();
        LocalDate today = LocalDate.now(SHANGHAI);
        List<AiUsageDaily> rows = aiUsageDailyMapper.selectPage(new Page<>(1, 1, false),
                Wrappers.lambdaQuery(AiUsageDaily.class)
                        .eq(AiUsageDaily::getUserId, userId)
                        .eq(AiUsageDaily::getUsageDate, today)
                        .eq(AiUsageDaily::getDeleteState, 0)).getRecords();
        if (!rows.isEmpty()) {
            AiUsageDaily daily = rows.get(0);
            vo.setQwenFlashUsed(daily.getQwenFlashUsed());
            vo.setAdvancedLlmUsed(daily.getAdvancedLlmUsed());
            vo.setImageNormalUsed(daily.getImageNormalUsed());
            vo.setImagePremiumUsed(daily.getImagePremiumUsed());
            vo.setCompanionNormalUsed(daily.getCompanionNormalUsed());
            vo.setCompanionPremiumUsed(daily.getCompanionPremiumUsed());
        }
        Date periodStart = new Date(periodStartMs);
        Date periodEnd = new Date(periodEndMs);
        Map<String, Long> tokenByModel = new HashMap<>();
        List<Map<String, Object>> tokenRows = forumAiUsageLogMapper.sumTokensByModelBetween(
                userId, periodStart, periodEnd);
        for (Map<String, Object> row : tokenRows) {
            String model = Objects.toString(row.get("modelCode"), "");
            if (model.isBlank()) {
                continue;
            }
            long in = toLong(row.get("inputTokens"));
            long out = toLong(row.get("outputTokens"));
            tokenByModel.put(model, in + out);
        }
        vo.setTokenByModel(tokenByModel);
        vo.setTotalCalls(forumAiUsageLogMapper.countCallsBetween(userId, periodStart, periodEnd));
        return vo;
    }

    private long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
