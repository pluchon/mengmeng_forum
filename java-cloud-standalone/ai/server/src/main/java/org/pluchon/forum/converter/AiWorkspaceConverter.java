package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.ForumAiCreationVersion;
import org.pluchon.forum.entity.db.ForumAiCreationWorkspace;
import org.pluchon.forum.entity.db.ForumAiLongTermMemory;
import org.pluchon.forum.entity.db.ForumAiTaskSession;
import org.pluchon.forum.entity.vo.ai.AiLongTermMemoryVO;
import org.pluchon.forum.entity.vo.ai.AiTaskSessionVO;
import org.pluchon.forum.entity.vo.ai.AiWorkspaceVO;
import org.pluchon.forum.entity.vo.ai.AiWorkspaceVersionVO;

// AI 工作区领域对象转换
public final class AiWorkspaceConverter {

    private AiWorkspaceConverter() {
    }

    public static AiWorkspaceVO toWorkspaceVO(ForumAiCreationWorkspace row) {
        AiWorkspaceVO vo = new AiWorkspaceVO();
        vo.setId(row.getId());
        vo.setCompanionSessionId(row.getCompanionSessionId());
        vo.setWorkspaceState(row.getWorkspaceState());
        vo.setSelectedVersionId(row.getSelectedVersionId());
        vo.setCheckpointId(row.getCheckpointId());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    public static AiWorkspaceVersionVO toVersionVO(ForumAiCreationVersion row) {
        AiWorkspaceVersionVO vo = new AiWorkspaceVersionVO();
        vo.setId(row.getId());
        vo.setParentVersionId(row.getParentVersionId());
        vo.setArtifactType(row.getArtifactType());
        vo.setVersionNo(row.getVersionNo());
        vo.setArtifactJson(row.getArtifactJson());
        vo.setSelected(Byte.valueOf((byte) 1).equals(row.getSelected()));
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    public static AiLongTermMemoryVO toMemoryVO(ForumAiLongTermMemory row) {
        AiLongTermMemoryVO vo = new AiLongTermMemoryVO();
        vo.setId(row.getId());
        vo.setSourceSessionId(row.getSourceSessionId());
        vo.setMemoryType(row.getMemoryType());
        vo.setContent(row.getContent());
        vo.setEnabled(Byte.valueOf((byte) 1).equals(row.getEnabled()));
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    public static AiTaskSessionVO toTaskSessionVO(ForumAiTaskSession row) {
        AiTaskSessionVO vo = new AiTaskSessionVO();
        vo.setId(row.getId());
        vo.setCompanionSessionId(row.getCompanionSessionId());
        vo.setWorkspaceId(row.getWorkspaceId());
        vo.setActiveModule(row.getActiveModule());
        vo.setActiveWorker(row.getActiveWorker());
        vo.setCheckpointId(row.getCheckpointId());
        vo.setTaskMode(row.getTaskMode());
        vo.setTaskState(row.getTaskState());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
