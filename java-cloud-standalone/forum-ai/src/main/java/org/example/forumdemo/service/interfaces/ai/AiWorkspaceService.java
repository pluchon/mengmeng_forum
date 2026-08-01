package org.example.forumdemo.service.interfaces.ai;

import org.example.forumdemo.entity.dto.ai.AiMemoryCreateRequest;
import org.example.forumdemo.entity.dto.ai.AiTaskHandoffRequest;
import org.example.forumdemo.entity.dto.ai.AiWorkspaceArtifactRequest;
import org.example.forumdemo.entity.dto.ai.AiWorkspaceCreateRequest;
import org.example.forumdemo.entity.vo.ai.AiLongTermMemoryVO;
import org.example.forumdemo.entity.vo.ai.AiTaskSessionVO;
import org.example.forumdemo.entity.vo.ai.AiWorkspaceVO;
import org.example.forumdemo.entity.vo.ai.AiWorkspaceVersionVO;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

// AI 工作区、版本、会员记忆与任务接力边界
public interface AiWorkspaceService {

    AiWorkspaceVO createWorkspace(Long userId, AiWorkspaceCreateRequest request);

    PageResult<AiWorkspaceVO> listWorkspaces(Long userId, Integer pageNum, Integer pageSize);

    List<AiWorkspaceVersionVO> listVersions(Long userId, Long workspaceId);

    AiWorkspaceVersionVO appendArtifact(Long userId, Long workspaceId, AiWorkspaceArtifactRequest request);

    void selectVersion(Long userId, Long workspaceId, Long versionId);

    void deleteWorkspace(Long userId, Long workspaceId);

    Long ensureWorkspace(Long userId, Long workspaceId, String checkpointId);

    Long appendGeneratedArtifact(Long userId, Long workspaceId, Long parentVersionId,
                                 String artifactType, String artifactJson, String checkpointId);

    List<AiLongTermMemoryVO> listMemories(Long userId);

    AiLongTermMemoryVO createMemory(Long userId, AiMemoryCreateRequest request);

    void setMemoryEnabled(Long userId, Long memoryId, boolean enabled);

    void deleteMemory(Long userId, Long memoryId);

    AiTaskSessionVO handoff(Long userId, AiTaskHandoffRequest request);

    AiTaskSessionVO currentTask(Long userId, Long companionSessionId);

    void finishTask(Long userId, Long taskSessionId);
}
