package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.entity.dto.AiMemoryCreateRequest;
import org.pluchon.forum.entity.dto.AiTaskHandoffRequest;
import org.pluchon.forum.entity.dto.AiWorkspaceArtifactRequest;
import org.pluchon.forum.entity.dto.AiWorkspaceCreateRequest;
import org.pluchon.forum.entity.vo.ai.AiLongTermMemoryVO;
import org.pluchon.forum.entity.vo.ai.AiTaskSessionVO;
import org.pluchon.forum.entity.vo.ai.AiWorkspaceVO;
import org.pluchon.forum.entity.vo.ai.AiWorkspaceVersionVO;
import org.pluchon.forum.entity.vo.common.PageResult;

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
