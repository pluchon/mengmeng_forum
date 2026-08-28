package org.pluchon.forum.service.internal;

import org.pluchon.forum.api.ai.AiUsageDailyBucketsVO;
import org.pluchon.forum.entity.db.ForumAiQuotaPeriodUsage;
import org.pluchon.forum.mapper.ForumAiQuotaPeriodUsageMapper;
import org.pluchon.forum.mapper.ForumAiUsageLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// AI 域内部用量查询服务
@Service
public class AiUsageInternalService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Autowired
    private ForumAiUsageLogMapper forumAiUsageLogMapper;

    @Autowired
    private ForumAiQuotaPeriodUsageMapper forumAiQuotaPeriodUsageMapper;

    public AiUsageDailyBucketsVO usageSnapshot(Long userId, long periodStartMs, long periodEndMs) {
        AiUsageDailyBucketsVO vo = new AiUsageDailyBucketsVO();
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
        // 配额相关的已用量必须与判定同源，取周期表而非日志聚合：
        // 日志是不可变审计流水，额度重置卡清不掉它，否则重置后面板仍显示已用满。
        ForumAiQuotaPeriodUsage period = forumAiQuotaPeriodUsageMapper.selectByUserAndPeriod(
                userId, periodKey(periodStart, periodEnd));
        vo.setQwenCostMicros(period == null || period.getQwenUsedMicros() == null
                ? 0L : Math.max(0L, period.getQwenUsedMicros()));
        vo.setWanImageUsed(period == null || period.getWanUsedCount() == null
                ? 0 : Math.max(0, period.getWanUsedCount()));
        return vo;
    }

    // 额度重置卡：清零该周期已结算用量。
    // 不动 reserved——它代表在途请求，清了会超卖。
    @Transactional(rollbackFor = Exception.class)
    public void resetPeriodQuota(Long userId, long periodStartMs, long periodEndMs) {
        String key = periodKey(new Date(periodStartMs), new Date(periodEndMs));
        forumAiQuotaPeriodUsageMapper.ensurePeriod(userId, key);
        forumAiQuotaPeriodUsageMapper.resetUsage(userId, key);
    }

    // 与 AiQuotaServiceImpl.periodKey 同格式
    private String periodKey(Date start, Date end) {
        return start.toInstant().atZone(TAIPEI).toLocalDate()
                + "_" + end.toInstant().atZone(TAIPEI).toLocalDate();
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
