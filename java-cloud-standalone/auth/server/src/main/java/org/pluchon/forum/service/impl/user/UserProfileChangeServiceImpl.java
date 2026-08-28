package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.auth.client.AuthAiHubInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.RegexUtil;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.db.UserProfileChangeRequest;
import org.pluchon.forum.entity.dto.user.ProfileChangeRequest;
import org.pluchon.forum.entity.vo.user.ProfileChangeStatusVO;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.mapper.UserProfileChangeRequestMapper;
import org.pluchon.forum.service.interfaces.user.UserProfileChangeService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class UserProfileChangeServiceImpl implements UserProfileChangeService {

    private static final byte STATUS_PENDING = 0;
    private static final byte STATUS_PROCESSING = 1;
    private static final byte STATUS_APPROVED = 2;
    private static final byte STATUS_REJECTED = 3;
    private static final byte STATUS_FAILED = 4;
    private static final byte STATUS_SUPERSEDED = 5;

    private static final byte DELETE_FALSE = 0;

    private static final String FIELD_NICKNAME = "NICKNAME";
    private static final String FIELD_BIO = "BIO";

    @Autowired
    private UserProfileChangeRequestMapper requestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthAiHubInternalFeignClient aiHubInternalFeignClient;

    @Autowired
    @Qualifier("profileReviewExecutor")
    private Executor profileReviewExecutor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileChangeStatusVO submit(Long userId, ProfileChangeRequest request) {
        if (userId == null || request == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String fieldType = normalizeFieldType(request.getFieldType());
        String content = request.getContent() == null ? "" : request.getContent().trim();
        validateCandidate(userId, fieldType, content);

        requestMapper.update(null, new LambdaUpdateWrapper<UserProfileChangeRequest>()
                .eq(UserProfileChangeRequest::getUserId, userId)
                .eq(UserProfileChangeRequest::getFieldType, fieldType)
                .in(UserProfileChangeRequest::getReviewStatus, STATUS_PENDING, STATUS_PROCESSING)
                .eq(UserProfileChangeRequest::getDeleteState, DELETE_FALSE)
                .set(UserProfileChangeRequest::getReviewStatus, STATUS_SUPERSEDED)
                .set(UserProfileChangeRequest::getReviewReason, "已提交更新的内容"));

        UserProfileChangeRequest entity = new UserProfileChangeRequest();
        entity.setUserId(userId);
        entity.setFieldType(fieldType);
        entity.setCandidateContent(content);
        entity.setReviewStatus(STATUS_PENDING);
        entity.setRetryCount(0);
        entity.setDeleteState(DELETE_FALSE);
        requestMapper.insert(entity);
        TransactionHooks.afterCommit(() -> profileReviewExecutor.execute(() -> process(entity.getId())));
        return toVO(entity);
    }

    @Override
    public ProfileChangeStatusVO latest(Long userId, String fieldType) {
        String normalized = normalizeFieldType(fieldType);
        List<UserProfileChangeRequest> rows = requestMapper.selectList(
                new LambdaQueryWrapper<UserProfileChangeRequest>()
                        .eq(UserProfileChangeRequest::getUserId, userId)
                        .eq(UserProfileChangeRequest::getFieldType, normalized)
                        .eq(UserProfileChangeRequest::getDeleteState, DELETE_FALSE)
                        .orderByDesc(UserProfileChangeRequest::getId));
        UserProfileChangeRequest latest = rows.isEmpty() ? null : rows.get(0);
        return latest == null ? null : toVO(latest);
    }

    private void process(Long requestId) {
        int claimed = requestMapper.update(null, new LambdaUpdateWrapper<UserProfileChangeRequest>()
                .eq(UserProfileChangeRequest::getId, requestId)
                .eq(UserProfileChangeRequest::getReviewStatus, STATUS_PENDING)
                .eq(UserProfileChangeRequest::getDeleteState, DELETE_FALSE)
                .set(UserProfileChangeRequest::getReviewStatus, STATUS_PROCESSING));
        if (claimed != 1) {
            return;
        }
        UserProfileChangeRequest request = requestMapper.selectById(requestId);
        try {
            String prefix = FIELD_NICKNAME.equals(request.getFieldType()) ? "论坛用户昵称：" : "论坛用户个人简介：";
            String reason = StringUtils.hasText(request.getCandidateContent())
                    ? aiHubInternalFeignClient.validateText(prefix + request.getCandidateContent())
                    : null;
            if (StringUtils.hasText(reason)) {
                finish(request, STATUS_REJECTED, reason);
                return;
            }
            transactionTemplate.executeWithoutResult(status -> approve(request));
        } catch (Exception exception) {
            retryOrFail(request, exception.getMessage());
        }
    }

    private void approve(UserProfileChangeRequest request) {
        UserProfileChangeRequest current = requestMapper.selectById(request.getId());
        if (current == null || current.getReviewStatus() == null || current.getReviewStatus() != STATUS_PROCESSING) {
            return;
        }
        Long newerCount = requestMapper.selectCount(new LambdaQueryWrapper<UserProfileChangeRequest>()
                .eq(UserProfileChangeRequest::getUserId, current.getUserId())
                .eq(UserProfileChangeRequest::getFieldType, current.getFieldType())
                .gt(UserProfileChangeRequest::getId, current.getId())
                .eq(UserProfileChangeRequest::getDeleteState, DELETE_FALSE));
        if (newerCount != null && newerCount > 0) {
            finish(current, STATUS_SUPERSEDED, "已提交更新的内容");
            return;
        }
        if (FIELD_NICKNAME.equals(current.getFieldType()) && nicknameExists(current.getUserId(), current.getCandidateContent())) {
            finish(current, STATUS_REJECTED, "该昵称已被其他用户使用");
            return;
        }
        userService.applyReviewedProfileChange(current.getUserId(), current.getFieldType(), current.getCandidateContent());
        finish(current, STATUS_APPROVED, null);
    }

    private void retryOrFail(UserProfileChangeRequest request, String reason) {
        int retries = request.getRetryCount() == null ? 0 : request.getRetryCount();
        byte nextStatus = retries + 1 >= 3 ? STATUS_FAILED : STATUS_PENDING;
        requestMapper.update(null, new LambdaUpdateWrapper<UserProfileChangeRequest>()
                .eq(UserProfileChangeRequest::getId, request.getId())
                .eq(UserProfileChangeRequest::getReviewStatus, STATUS_PROCESSING)
                .set(UserProfileChangeRequest::getReviewStatus, nextStatus)
                .set(UserProfileChangeRequest::getRetryCount, retries + 1)
                .set(UserProfileChangeRequest::getReviewReason,
                        nextStatus == STATUS_FAILED ? "审核服务暂时不可用，请重新提交" : truncate(reason)));
    }

    private void finish(UserProfileChangeRequest request, byte status, String reason) {
        requestMapper.update(null, new LambdaUpdateWrapper<UserProfileChangeRequest>()
                .eq(UserProfileChangeRequest::getId, request.getId())
                .in(UserProfileChangeRequest::getReviewStatus, STATUS_PROCESSING, STATUS_PENDING)
                .set(UserProfileChangeRequest::getReviewStatus, status)
                .set(UserProfileChangeRequest::getReviewReason, truncate(reason))
                .set(UserProfileChangeRequest::getReviewedAt, new Date()));
    }

    private void validateCandidate(Long userId, String fieldType, String content) {
        if (FIELD_NICKNAME.equals(fieldType)) {
            if (!RegexUtil.checkNickname(content)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                        "昵称需为2–20位中文、英文字母或数字"));
            }
            if (nicknameExists(userId, content)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                        "该昵称已被其他用户使用"));
            }
            return;
        }
        if (content.length() > 50) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "个人简介不能超过50个字"));
        }
    }

    private boolean nicknameExists(Long userId, String nickname) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, nickname)
                .ne(User::getId, userId)
                .eq(User::getDeleteState, (byte) 0));
        return count != null && count > 0;
    }

    private String normalizeFieldType(String fieldType) {
        String normalized = fieldType == null ? "" : fieldType.trim().toUpperCase(Locale.ROOT);
        if (!FIELD_NICKNAME.equals(normalized) && !FIELD_BIO.equals(normalized)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "仅支持审核昵称或个人简介"));
        }
        return normalized;
    }

    private ProfileChangeStatusVO toVO(UserProfileChangeRequest request) {
        ProfileChangeStatusVO vo = new ProfileChangeStatusVO();
        vo.setRequestId(request.getId());
        vo.setFieldType(request.getFieldType());
        vo.setPendingContent(request.getCandidateContent());
        vo.setStatus(statusLabel(request.getReviewStatus()));
        vo.setReason(request.getReviewReason());
        vo.setSubmittedAt(request.getCreateTime());
        vo.setReviewedAt(request.getReviewedAt());
        return vo;
    }

    private String statusLabel(Byte status) {
        if (status == null || status == STATUS_PENDING) return "PENDING";
        if (status == STATUS_PROCESSING) return "PROCESSING";
        if (status == STATUS_APPROVED) return "APPROVED";
        if (status == STATUS_REJECTED) return "REJECTED";
        if (status == STATUS_FAILED) return "FAILED";
        return "SUPERSEDED";
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) return value;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
