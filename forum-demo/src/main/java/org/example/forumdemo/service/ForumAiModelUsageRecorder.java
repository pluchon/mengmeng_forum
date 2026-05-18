package org.example.forumdemo.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.mapper.ForumAiModelUsageDailyMapper;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 管理端「AI 调用趋势」数据源：在 BFF 成功转发大模型调用后累加 {@code forum_ai_model_usage_daily}。
 */
@Slf4j
@Service
public class ForumAiModelUsageRecorder {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private ForumAiModelUsageDailyMapper forumAiModelUsageDailyMapper;

    public void recordModelCall(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return;
        }
        String code = modelCode.trim();
        if (code.length() > 64) {
            code = code.substring(0, 64);
        }
        LocalDate today = LocalDate.now(ZONE);
        Date statDate = Date.valueOf(today);
        try {
            forumAiModelUsageDailyMapper.incrementCallCount(statDate, code);
        } catch (Exception e) {
            log.warn("AI 调用日汇总写入失败 model={} date={}", code, today, e);
        }
    }
}
