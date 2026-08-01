package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// AI Supervisor 任务会话
@Data
@TableName("forum_ai_task_session")
public class ForumAiTaskSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("companion_session_id")
    private Long companionSessionId;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("active_module")
    private String activeModule;

    @TableField("active_worker")
    private String activeWorker;

    @TableField("checkpoint_id")
    private String checkpointId;

    @TableField("task_mode")
    private String taskMode;

    @TableField("task_state")
    private String taskState;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
