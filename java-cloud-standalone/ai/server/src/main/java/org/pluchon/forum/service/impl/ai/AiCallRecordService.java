package org.pluchon.forum.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.AiCallState;
import org.pluchon.forum.common.metrics.ForumMetrics;
import org.pluchon.forum.entity.db.ForumAiCallRecord;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;
import org.pluchon.forum.entity.vo.ai.AiCallBeginResult;
import org.pluchon.forum.cloud.feign.AiPointsInternalFeignClient;
import org.pluchon.forum.mapper.ForumAiCallRecordMapper;
import org.pluchon.forum.service.security.AiUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// AI 调用预记录：调用前写入 PENDING，成功/失败/停止/断网分别结算
@Slf4j
@Service
public class AiCallRecordService {

    private static final int PARTIAL_CHARS_PER_TOKEN = 4;

    @Autowired
    private ForumAiCallRecordMapper forumAiCallRecordMapper;

    @Autowired
    private AiPointsBillingService aiPointsBillingService;

    @Autowired
    private AiPointsInternalFeignClient aiPointsInternalFeignClient;

    @Autowired
    private ForumMetrics forumMetrics;

    // 调用 AI 前预写记录。clientRequestId 为空时不建预记录 兼容旧客户端
    @Transactional(rollbackFor = Exception.class)
    public AiCallBeginResult beginCall(Long userId, String featureCode, String clientRequestId, String modelCode) {
        if (userId == null || !StringUtils.hasText(featureCode) || !StringUtils.hasText(clientRequestId)) {
            return null;
        }
        String requestId = clientRequestId.trim();
        if (requestId.length() > 64) {
            requestId = requestId.substring(0, 64);
        }
        ForumAiCallRecord existing = forumAiCallRecordMapper.selectOne(
                Wrappers.lambdaQuery(ForumAiCallRecord.class)
                        .eq(ForumAiCallRecord::getUserId, userId)
                        .eq(ForumAiCallRecord::getFeatureCode, featureCode.trim())
                        .eq(ForumAiCallRecord::getClientRequestId, requestId));
        if (existing != null) {
            AiCallState state = AiCallState.fromCode(existing.getCallState());
            if (state == AiCallState.SUCCESS) {
                forumMetrics.recordIdempotencyHit();
                int charged = existing.getPointsCharged() != null ? existing.getPointsCharged() : 0;
                return AiCallBeginResult.duplicateSuccess(existing.getId(), charged);
            }
            if (state == AiCallState.FAILED || state == AiCallState.TIMEOUT
                    || state == AiCallState.DISCONNECTED) {
                return AiCallBeginResult.terminalFailure(existing.getId());
            }
            return AiCallBeginResult.pending(existing.getId());
        }
        int estimated = aiPointsBillingService.estimatePoints(
                modelCode,
                Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS,
                Constant.AI_ESTIMATE_CHAT_OUTPUT_TOKENS,
                0);
        ForumAiCallRecord row = new ForumAiCallRecord();
        row.setUserId(userId);
        row.setFeatureCode(featureCode.trim());
        row.setClientRequestId(requestId);
        row.setModelCode(modelCode);
        row.setCallState(AiCallState.PENDING.getCode());
        row.setEstimatedPoints(estimated);
        row.setPointsCharged(0);
        row.setInputTokens(0);
        row.setOutputTokens(0);
        row.setDeleteState((byte) 0);
        Date now = new Date();
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            forumAiCallRecordMapper.insert(row);
            return AiCallBeginResult.pending(row.getId());
        } catch (DuplicateKeyException dup) {
            return beginCall(userId, featureCode, clientRequestId, modelCode);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settleSuccess(AiCallBeginResult begin, AiUserContext user, String featureCode,
                                             AiModelUsageDTO usage, String relatedId, long latencyMs) {
        if (begin == null) {
            return aiPointsBillingService.bill(user, featureCode, usage, relatedId);
        }
        if (begin.isDuplicateSuccess()) {
            return duplicateBillingResult(user, begin.getPreviousPointsCharged());
        }
        if (begin.isTerminalFailure()) {
            forumMetrics.recordIdempotencyHit();
            return duplicateBillingResult(user, 0);
        }
        forumMetrics.recordAiCallLatency(latencyMs);
        Map<String, Object> billing = aiPointsBillingService.bill(
                user, featureCode, usage, relatedId);
        updateRecordSettled(begin.getRecordId(), AiCallState.SUCCESS, usage, billing);
        return billing;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settleSuccessBatch(
            AiCallBeginResult begin,
            AiUserContext user,
            String featureCode,
            List<AiModelUsageDTO> usages,
            String fallbackModel,
            String relatedId,
            long latencyMs) {
        AiModelUsageDTO total = aiPointsBillingService.aggregateUsage(usages, fallbackModel);
        if (begin != null && begin.isDuplicateSuccess()) {
            return duplicateBillingResult(user, begin.getPreviousPointsCharged());
        }
        if (begin != null && begin.isTerminalFailure()) {
            forumMetrics.recordIdempotencyHit();
            return duplicateBillingResult(user, 0);
        }
        forumMetrics.recordAiCallLatency(latencyMs);
        Map<String, Object> billing = aiPointsBillingService.billBatch(
                user, featureCode, usages, fallbackModel, relatedId);
        if (begin != null) {
            updateRecordSettled(begin.getRecordId(), AiCallState.SUCCESS, total, billing);
        }
        return billing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailure(AiCallBeginResult begin, AiCallState state, String errorSummary) {
        if (begin == null || begin.getRecordId() == null) {
            return;
        }
        if (begin.isDuplicateSuccess() || begin.isTerminalFailure()) {
            return;
        }
        forumMetrics.recordAiCallFailure();
        String summary = sanitizeSummary(errorSummary);
        forumAiCallRecordMapper.update(null, Wrappers.lambdaUpdate(ForumAiCallRecord.class)
                .eq(ForumAiCallRecord::getId, begin.getRecordId())
                .eq(ForumAiCallRecord::getCallState, AiCallState.PENDING.getCode())
                .set(ForumAiCallRecord::getCallState, state.getCode())
                .set(ForumAiCallRecord::getErrorSummary, summary)
                .set(ForumAiCallRecord::getPointsCharged, 0));
    }

    // 流式停止或断网但有部分输出：按实际输出 token 计费；无输出则不扣费
    @Transactional(rollbackFor = Exception.class)
    public void settlePartialOutput(AiCallBeginResult begin, AiUserContext user, String featureCode,
                                    String modelCode, int outputCharCount, String relatedId) {
        if (begin == null) {
            return;
        }
        if (begin.isDuplicateSuccess()) {
            duplicateBillingResult(user, begin.getPreviousPointsCharged());
            return;
        }
        if (outputCharCount <= 0) {
            markFailure(begin, AiCallState.DISCONNECTED, "无输出断开");
            duplicateBillingResult(user, 0);
            return;
        }
        int outputTokens = Math.max(1, outputCharCount / PARTIAL_CHARS_PER_TOKEN);
        AiModelUsageDTO usage = new AiModelUsageDTO();
        usage.setModelCode(modelCode);
        usage.setInputTokens(Constant.AI_ESTIMATE_CHAT_INPUT_TOKENS);
        usage.setOutputTokens(outputTokens);
        usage.setEstimated(true);
        Map<String, Object> billing = aiPointsBillingService.bill(
                user, featureCode, usage, relatedId);
        updateRecordSettled(begin.getRecordId(), AiCallState.STOPPED, usage, billing);
    }

    private void updateRecordSettled(Long recordId, AiCallState state, AiModelUsageDTO usage,
                                     Map<String, Object> billing) {
        int charged = billing.get("pointsCost") instanceof Number n ? n.intValue() : 0;
        forumAiCallRecordMapper.update(null, Wrappers.lambdaUpdate(ForumAiCallRecord.class)
                .eq(ForumAiCallRecord::getId, recordId)
                .set(ForumAiCallRecord::getCallState, state.getCode())
                .set(ForumAiCallRecord::getPointsCharged, charged)
                .set(ForumAiCallRecord::getInputTokens,
                        usage.getInputTokens() != null ? usage.getInputTokens() : 0)
                .set(ForumAiCallRecord::getOutputTokens,
                        usage.getOutputTokens() != null ? usage.getOutputTokens() : 0));
    }

    private Map<String, Object> duplicateBillingResult(AiUserContext user, int pointsCharged) {
        Integer balance = aiPointsInternalFeignClient.getBalance(user.getId());
        int balanceAfter = balance == null ? 0 : balance;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pointsCost", pointsCharged);
        out.put("balanceAfter", balanceAfter);
        out.put("billingMode", "duplicate");
        out.put("usageStats", Map.of("pointsCost", pointsCharged));
        return out;
    }

    private static String sanitizeSummary(String errorSummary) {
        if (!StringUtils.hasText(errorSummary)) {
            return "调用失败";
        }
        String s = errorSummary.trim();
        if (s.length() > 200) {
            return s.substring(0, 200);
        }
        return s;
    }
}
