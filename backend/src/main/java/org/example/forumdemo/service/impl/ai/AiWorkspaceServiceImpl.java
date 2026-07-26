package org.example.forumdemo.service.impl.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.AiTaskMode;
import org.example.forumdemo.common.enums.AiTaskState;
import org.example.forumdemo.common.enums.AiWorkspaceState;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.converter.AiWorkspaceConverter;
import org.example.forumdemo.entity.db.ForumAiCreationVersion;
import org.example.forumdemo.entity.db.ForumAiCreationWorkspace;
import org.example.forumdemo.entity.db.ForumAiLongTermMemory;
import org.example.forumdemo.entity.db.ForumAiTaskSession;
import org.example.forumdemo.entity.db.ForumCompanionSession;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiMemoryCreateRequest;
import org.example.forumdemo.entity.dto.ai.AiTaskHandoffRequest;
import org.example.forumdemo.entity.dto.ai.AiWorkspaceArtifactRequest;
import org.example.forumdemo.entity.dto.ai.AiWorkspaceCreateRequest;
import org.example.forumdemo.entity.vo.ai.AiLongTermMemoryVO;
import org.example.forumdemo.entity.vo.ai.AiTaskSessionVO;
import org.example.forumdemo.entity.vo.ai.AiWorkspaceVO;
import org.example.forumdemo.entity.vo.ai.AiWorkspaceVersionVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.mapper.ForumAiCreationVersionMapper;
import org.example.forumdemo.mapper.ForumAiCreationWorkspaceMapper;
import org.example.forumdemo.mapper.ForumAiLongTermMemoryMapper;
import org.example.forumdemo.mapper.ForumAiTaskSessionMapper;
import org.example.forumdemo.mapper.ForumCompanionSessionMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.ai.AiWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// AI 工作区、版本和用户可控记忆的 Java 业务权威
@Service
public class AiWorkspaceServiceImpl implements AiWorkspaceService {

    private static final int MAX_PAGE_SIZE = 30;
    private static final int MAX_ARTIFACT_LENGTH = 200_000;
    private static final int MAX_MEMORY_LENGTH = 1_000;
    private static final Set<String> ARTIFACT_TYPES = Set.of(
            "WRITE", "COVER_HINTS", "COVER_IMAGE", "TITLE", "TAGS", "CREATION_RESULT");
    private static final Set<String> MEMORY_TYPES = Set.of("PREFERENCE", "WRITING_STYLE", "TOPIC");

    @Autowired
    private ForumAiCreationWorkspaceMapper workspaceMapper;

    @Autowired
    private ForumAiCreationVersionMapper versionMapper;

    @Autowired
    private ForumAiLongTermMemoryMapper memoryMapper;

    @Autowired
    private ForumAiTaskSessionMapper taskSessionMapper;

    @Autowired
    private ForumCompanionSessionMapper companionSessionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiWorkspaceVO createWorkspace(Long userId, AiWorkspaceCreateRequest request) {
        requireUser(userId);
        AiWorkspaceCreateRequest safeRequest = request == null ? new AiWorkspaceCreateRequest() : request;
        ForumAiCreationWorkspace row = createWorkspaceRow(userId, safeRequest.getCompanionSessionId(), safeRequest.getCheckpointId());
        return AiWorkspaceConverter.toWorkspaceVO(row);
    }

    @Override
    public PageResult<AiWorkspaceVO> listWorkspaces(Long userId, Integer pageNum, Integer pageSize) {
        requireUser(userId);
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<ForumAiCreationWorkspace> page = workspaceMapper.selectPage(new Page<>(safePageNum, safePageSize),
                new LambdaQueryWrapper<ForumAiCreationWorkspace>()
                        .eq(ForumAiCreationWorkspace::getUserId, userId)
                        .eq(ForumAiCreationWorkspace::getDeleteState, (byte) 0)
                        .orderByDesc(ForumAiCreationWorkspace::getUpdateTime)
                        .orderByDesc(ForumAiCreationWorkspace::getId));
        List<AiWorkspaceVO> records = new ArrayList<>();
        for (ForumAiCreationWorkspace row : page.getRecords()) {
            records.add(AiWorkspaceConverter.toWorkspaceVO(row));
        }
        return new PageResult<>(records, page.getTotal(), safePageNum, safePageSize,
                page.getPages(), page.hasNext());
    }

    @Override
    public List<AiWorkspaceVersionVO> listVersions(Long userId, Long workspaceId) {
        requireWorkspace(userId, workspaceId);
        List<ForumAiCreationVersion> rows = versionMapper.selectList(
                new LambdaQueryWrapper<ForumAiCreationVersion>()
                        .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                        .eq(ForumAiCreationVersion::getDeleteState, (byte) 0)
                        .orderByAsc(ForumAiCreationVersion::getCreateTime)
                        .orderByAsc(ForumAiCreationVersion::getId));
        List<AiWorkspaceVersionVO> result = new ArrayList<>();
        for (ForumAiCreationVersion row : rows) {
            result.add(AiWorkspaceConverter.toVersionVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiWorkspaceVersionVO appendArtifact(Long userId, Long workspaceId, AiWorkspaceArtifactRequest request) {
        if (request == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ForumAiCreationVersion row = appendArtifactRow(userId, workspaceId, request.getParentVersionId(),
                request.getArtifactType(), request.getArtifactJson(), request.getCheckpointId());
        return AiWorkspaceConverter.toVersionVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void selectVersion(Long userId, Long workspaceId, Long versionId) {
        requireWorkspace(userId, workspaceId);
        ForumAiCreationVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<ForumAiCreationVersion>()
                        .eq(ForumAiCreationVersion::getId, versionId)
                        .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                        .eq(ForumAiCreationVersion::getDeleteState, (byte) 0));
        if (version == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        versionMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationVersion>()
                .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                .eq(ForumAiCreationVersion::getDeleteState, (byte) 0)
                .set(ForumAiCreationVersion::getSelected, (byte) 0));
        versionMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationVersion>()
                .eq(ForumAiCreationVersion::getId, versionId)
                .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                .set(ForumAiCreationVersion::getSelected, (byte) 1)
                .set(ForumAiCreationVersion::getUpdateTime, new Date()));
        workspaceMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationWorkspace>()
                .eq(ForumAiCreationWorkspace::getId, workspaceId)
                .eq(ForumAiCreationWorkspace::getUserId, userId)
                .eq(ForumAiCreationWorkspace::getDeleteState, (byte) 0)
                .set(ForumAiCreationWorkspace::getSelectedVersionId, versionId)
                .set(ForumAiCreationWorkspace::getUpdateTime, new Date()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkspace(Long userId, Long workspaceId) {
        requireWorkspace(userId, workspaceId);
        Date now = new Date();
        versionMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationVersion>()
                .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                .eq(ForumAiCreationVersion::getDeleteState, (byte) 0)
                .set(ForumAiCreationVersion::getDeleteState, (byte) 1)
                .set(ForumAiCreationVersion::getUpdateTime, now));
        workspaceMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationWorkspace>()
                .eq(ForumAiCreationWorkspace::getId, workspaceId)
                .eq(ForumAiCreationWorkspace::getUserId, userId)
                .eq(ForumAiCreationWorkspace::getDeleteState, (byte) 0)
                .set(ForumAiCreationWorkspace::getDeleteState, (byte) 1)
                .set(ForumAiCreationWorkspace::getUpdateTime, now));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureWorkspace(Long userId, Long workspaceId, String checkpointId) {
        if (workspaceId != null) {
            ForumAiCreationWorkspace existing = requireWorkspace(userId, workspaceId);
            if (StringUtils.hasText(checkpointId)) {
                workspaceMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationWorkspace>()
                        .eq(ForumAiCreationWorkspace::getId, existing.getId())
                        .eq(ForumAiCreationWorkspace::getUserId, userId)
                        .set(ForumAiCreationWorkspace::getCheckpointId, normalizeCheckpoint(checkpointId))
                        .set(ForumAiCreationWorkspace::getUpdateTime, new Date()));
            }
            return existing.getId();
        }
        return createWorkspaceRow(userId, null, checkpointId).getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long appendGeneratedArtifact(Long userId, Long workspaceId, Long parentVersionId,
                                        String artifactType, String artifactJson, String checkpointId) {
        return appendArtifactRow(userId, workspaceId, parentVersionId, artifactType, artifactJson, checkpointId).getId();
    }

    @Override
    public List<AiLongTermMemoryVO> listMemories(Long userId) {
        requireActiveVip(userId);
        List<ForumAiLongTermMemory> rows = memoryMapper.selectList(
                new LambdaQueryWrapper<ForumAiLongTermMemory>()
                        .eq(ForumAiLongTermMemory::getUserId, userId)
                        .eq(ForumAiLongTermMemory::getDeleteState, (byte) 0)
                        .orderByDesc(ForumAiLongTermMemory::getUpdateTime)
                        .orderByDesc(ForumAiLongTermMemory::getId));
        List<AiLongTermMemoryVO> result = new ArrayList<>();
        for (ForumAiLongTermMemory row : rows) {
            result.add(AiWorkspaceConverter.toMemoryVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiLongTermMemoryVO createMemory(Long userId, AiMemoryCreateRequest request) {
        requireActiveVip(userId);
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String memoryType = normalizeEnumValue(request.getMemoryType(), MEMORY_TYPES);
        String content = request.getContent().trim();
        if (content.length() > MAX_MEMORY_LENGTH) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (request.getSourceSessionId() != null) {
            requireCompanionSession(userId, request.getSourceSessionId());
        }
        ForumAiLongTermMemory row = new ForumAiLongTermMemory();
        row.setUserId(userId);
        row.setSourceSessionId(request.getSourceSessionId());
        row.setMemoryType(memoryType);
        row.setContent(content);
        row.setEnabled((byte) 1);
        row.setDeleteState((byte) 0);
        row.setCreateTime(new Date());
        row.setUpdateTime(new Date());
        memoryMapper.insert(row);
        return AiWorkspaceConverter.toMemoryVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setMemoryEnabled(Long userId, Long memoryId, boolean enabled) {
        requireActiveVip(userId);
        int changed = memoryMapper.update(null, new LambdaUpdateWrapper<ForumAiLongTermMemory>()
                .eq(ForumAiLongTermMemory::getId, memoryId)
                .eq(ForumAiLongTermMemory::getUserId, userId)
                .eq(ForumAiLongTermMemory::getDeleteState, (byte) 0)
                .set(ForumAiLongTermMemory::getEnabled, enabled ? (byte) 1 : (byte) 0)
                .set(ForumAiLongTermMemory::getUpdateTime, new Date()));
        if (changed < 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMemory(Long userId, Long memoryId) {
        requireActiveVip(userId);
        int changed = memoryMapper.update(null, new LambdaUpdateWrapper<ForumAiLongTermMemory>()
                .eq(ForumAiLongTermMemory::getId, memoryId)
                .eq(ForumAiLongTermMemory::getUserId, userId)
                .eq(ForumAiLongTermMemory::getDeleteState, (byte) 0)
                .set(ForumAiLongTermMemory::getDeleteState, (byte) 1)
                .set(ForumAiLongTermMemory::getUpdateTime, new Date()));
        if (changed < 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskSessionVO handoff(Long userId, AiTaskHandoffRequest request) {
        requireUser(userId);
        if (request == null || !StringUtils.hasText(request.getActiveModule())
                || !StringUtils.hasText(request.getActiveWorker())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (request.getCompanionSessionId() != null) {
            requireCompanionSession(userId, request.getCompanionSessionId());
        }
        if (request.getWorkspaceId() != null) {
            requireWorkspace(userId, request.getWorkspaceId());
        }
        ForumAiTaskSession current = findActiveTask(userId, request.getCompanionSessionId());
        Date now = new Date();
        if (current == null) {
            current = new ForumAiTaskSession();
            current.setUserId(userId);
            current.setCompanionSessionId(request.getCompanionSessionId());
            current.setDeleteState((byte) 0);
            current.setCreateTime(now);
        }
        current.setWorkspaceId(request.getWorkspaceId());
        current.setActiveModule(normalizeShortText(request.getActiveModule(), 64));
        current.setActiveWorker(normalizeShortText(request.getActiveWorker(), 64));
        current.setCheckpointId(normalizeCheckpoint(request.getCheckpointId()));
        current.setTaskMode(normalizeTaskMode(request.getTaskMode()));
        current.setTaskState(AiTaskState.ACTIVE.name());
        current.setUpdateTime(now);
        if (current.getId() == null) {
            taskSessionMapper.insert(current);
        } else {
            taskSessionMapper.updateById(current);
        }
        return AiWorkspaceConverter.toTaskSessionVO(current);
    }

    @Override
    public AiTaskSessionVO currentTask(Long userId, Long companionSessionId) {
        requireUser(userId);
        ForumAiTaskSession row = findActiveTask(userId, companionSessionId);
        return row == null ? null : AiWorkspaceConverter.toTaskSessionVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishTask(Long userId, Long taskSessionId) {
        requireUser(userId);
        int changed = taskSessionMapper.update(null, new LambdaUpdateWrapper<ForumAiTaskSession>()
                .eq(ForumAiTaskSession::getId, taskSessionId)
                .eq(ForumAiTaskSession::getUserId, userId)
                .eq(ForumAiTaskSession::getDeleteState, (byte) 0)
                .eq(ForumAiTaskSession::getTaskState, AiTaskState.ACTIVE.name())
                .set(ForumAiTaskSession::getTaskState, AiTaskState.COMPLETED.name())
                .set(ForumAiTaskSession::getUpdateTime, new Date()));
        if (changed < 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private ForumAiCreationWorkspace createWorkspaceRow(Long userId, Long companionSessionId, String checkpointId) {
        requireUser(userId);
        if (companionSessionId != null) {
            requireCompanionSession(userId, companionSessionId);
        }
        ForumAiCreationWorkspace row = new ForumAiCreationWorkspace();
        row.setUserId(userId);
        row.setCompanionSessionId(companionSessionId);
        row.setWorkspaceState(AiWorkspaceState.ACTIVE.name());
        row.setCheckpointId(normalizeCheckpoint(checkpointId));
        row.setDeleteState((byte) 0);
        row.setCreateTime(new Date());
        row.setUpdateTime(new Date());
        workspaceMapper.insert(row);
        return row;
    }

    private ForumAiCreationVersion appendArtifactRow(Long userId, Long workspaceId, Long parentVersionId,
                                                      String artifactTypeRaw, String artifactJsonRaw, String checkpointId) {
        ForumAiCreationWorkspace workspace = requireWorkspace(userId, workspaceId);
        String artifactType = normalizeEnumValue(artifactTypeRaw, ARTIFACT_TYPES);
        String artifactJson = normalizeArtifactJson(artifactJsonRaw);
        if (parentVersionId != null) {
            ForumAiCreationVersion parent = versionMapper.selectOne(
                    new LambdaQueryWrapper<ForumAiCreationVersion>()
                            .eq(ForumAiCreationVersion::getId, parentVersionId)
                            .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                            .eq(ForumAiCreationVersion::getDeleteState, (byte) 0));
            if (parent == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
            }
        }
        int nextVersion = nextVersionNo(workspaceId, artifactType);
        ForumAiCreationVersion row = new ForumAiCreationVersion();
        row.setWorkspaceId(workspaceId);
        row.setParentVersionId(parentVersionId);
        row.setArtifactType(artifactType);
        row.setVersionNo(nextVersion);
        row.setArtifactJson(artifactJson);
        row.setSelected((byte) 0);
        row.setDeleteState((byte) 0);
        row.setCreateTime(new Date());
        row.setUpdateTime(new Date());
        versionMapper.insert(row);
        workspaceMapper.update(null, new LambdaUpdateWrapper<ForumAiCreationWorkspace>()
                .eq(ForumAiCreationWorkspace::getId, workspace.getId())
                .eq(ForumAiCreationWorkspace::getUserId, userId)
                .set(StringUtils.hasText(checkpointId), ForumAiCreationWorkspace::getCheckpointId,
                        normalizeCheckpoint(checkpointId))
                .set(ForumAiCreationWorkspace::getUpdateTime, new Date()));
        return row;
    }

    private int nextVersionNo(Long workspaceId, String artifactType) {
        List<ForumAiCreationVersion> latest = versionMapper.selectPage(new Page<>(1, 1, false),
                new LambdaQueryWrapper<ForumAiCreationVersion>()
                        .eq(ForumAiCreationVersion::getWorkspaceId, workspaceId)
                        .eq(ForumAiCreationVersion::getArtifactType, artifactType)
                        .eq(ForumAiCreationVersion::getDeleteState, (byte) 0)
                        .orderByDesc(ForumAiCreationVersion::getVersionNo)).getRecords();
        return latest.isEmpty() ? 1 : latest.get(0).getVersionNo() + 1;
    }

    private ForumAiCreationWorkspace requireWorkspace(Long userId, Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ForumAiCreationWorkspace row = workspaceMapper.selectOne(
                new LambdaQueryWrapper<ForumAiCreationWorkspace>()
                        .eq(ForumAiCreationWorkspace::getId, workspaceId)
                        .eq(ForumAiCreationWorkspace::getUserId, userId)
                        .eq(ForumAiCreationWorkspace::getDeleteState, (byte) 0));
        if (row == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return row;
    }

    private void requireCompanionSession(Long userId, Long companionSessionId) {
        ForumCompanionSession row = companionSessionMapper.selectOne(
                new LambdaQueryWrapper<ForumCompanionSession>()
                        .eq(ForumCompanionSession::getId, companionSessionId)
                        .eq(ForumCompanionSession::getUserId, userId)
                        .eq(ForumCompanionSession::getDeleteState, (byte) 0));
        if (row == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    private User requireActiveVip(Long userId) {
        User user = requireUser(userId);
        Byte tier = user.getVipTier();
        if (tier == null || Constant.VIP_TIER_FREE.equals(tier)
                || (user.getVipExpireAt() != null && !user.getVipExpireAt().after(new Date()))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "长期记忆仅向有效会员开放"));
        }
        return user;
    }

    private ForumAiTaskSession findActiveTask(Long userId, Long companionSessionId) {
        LambdaQueryWrapper<ForumAiTaskSession> wrapper = new LambdaQueryWrapper<ForumAiTaskSession>()
                .eq(ForumAiTaskSession::getUserId, userId)
                .eq(ForumAiTaskSession::getDeleteState, (byte) 0)
                .eq(ForumAiTaskSession::getTaskState, AiTaskState.ACTIVE.name())
                .orderByDesc(ForumAiTaskSession::getUpdateTime)
                .last("LIMIT 1");
        if (companionSessionId == null) {
            wrapper.isNull(ForumAiTaskSession::getCompanionSessionId);
        } else {
            wrapper.eq(ForumAiTaskSession::getCompanionSessionId, companionSessionId);
        }
        return taskSessionMapper.selectOne(wrapper);
    }

    private String normalizeArtifactJson(String raw) {
        if (!StringUtils.hasText(raw) || raw.trim().length() > MAX_ARTIFACT_LENGTH) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        try {
            JsonNode node = objectMapper.readTree(raw.trim());
            if (node == null || node.isNull()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "artifactJson 必须是合法 JSON"));
        }
    }

    private String normalizeEnumValue(String raw, Set<String> allowedValues) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(value)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return value;
    }

    private String normalizeTaskMode(String raw) {
        String value = raw == null || raw.isBlank() ? AiTaskMode.ASSISTANT.name() : raw.trim().toUpperCase(Locale.ROOT);
        try {
            return AiTaskMode.valueOf(value).name();
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private String normalizeCheckpoint(String checkpointId) {
        if (!StringUtils.hasText(checkpointId)) {
            return null;
        }
        return normalizeShortText(checkpointId, 128);
    }

    private String normalizeShortText(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return value.trim();
    }
}
