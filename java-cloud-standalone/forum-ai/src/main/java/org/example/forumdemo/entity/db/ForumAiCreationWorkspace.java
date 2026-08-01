package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// AI 创作工作区
@Data
@TableName("forum_ai_creation_workspace")
public class ForumAiCreationWorkspace {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("companion_session_id")
    private Long companionSessionId;

    @TableField("workspace_state")
    private String workspaceState;

    @TableField("selected_version_id")
    private Long selectedVersionId;

    @TableField("checkpoint_id")
    private String checkpointId;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
