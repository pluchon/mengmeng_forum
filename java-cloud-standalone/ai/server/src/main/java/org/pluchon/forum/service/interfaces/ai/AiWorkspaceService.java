package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.entity.dto.AiMemoryCreateRequest;
import org.pluchon.forum.entity.dto.AiWorkspaceArtifactRequest;
import org.pluchon.forum.entity.vo.ai.AiLongTermMemoryVO;
import org.pluchon.forum.entity.vo.ai.AiWorkspaceVO;
import org.pluchon.forum.entity.vo.ai.AiWorkspaceVersionVO;
import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

// AI 工作区、版本与会员记忆边界
public interface AiWorkspaceService {

    PageResult<AiWorkspaceVO> listWorkspaces(Long userId, Integer pageNum, Integer pageSize);

    List<AiWorkspaceVersionVO> listVersions(Long userId, Long workspaceId);

    AiWorkspaceVersionVO appendArtifact(Long userId, Long workspaceId, AiWorkspaceArtifactRequest request);

    void selectVersion(Long userId, Long workspaceId, Long versionId);

    Long ensureWorkspace(Long userId, Long workspaceId, String checkpointId);

    Long appendGeneratedArtifact(Long userId, Long workspaceId, Long parentVersionId,
                                 String artifactType, String artifactJson, String checkpointId);

    List<AiLongTermMemoryVO> listMemories(Long userId);

    AiLongTermMemoryVO createMemory(Long userId, AiMemoryCreateRequest request);

    void setMemoryEnabled(Long userId, Long memoryId, boolean enabled);

    void deleteMemory(Long userId, Long memoryId);
}
