package org.pluchon.forum.service.internal;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.api.ai.AiUsageDailyBucketsVO;
import org.pluchon.forum.entity.db.AiUsageDaily;
import org.pluchon.forum.mapper.AiUsageDailyMapper;
import org.pluchon.forum.mapper.ForumAiUsageLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// AI 域内部用量查询服务
@Service
public class AiUsageInternalService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AiUsageDailyMapper aiUsageDailyMapper;

    @Autowired
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    public AiUsageDailyBucketsVO usageSnapshot(Long userId, long periodStartMs, long periodEndMs) {
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
            tokenByModel.put(model, toLong(row.get("inputTokens")) + toLong(row.get("outputTokens")));
        }
        vo.setTokenByModel(tokenByModel);
        vo.setTotalCalls(forumAiUsageLogMapper.countCallsBetween(userId, periodStart, periodEnd));
        return vo;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
