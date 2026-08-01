package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// AI 创作产物版本
@Data
@TableName("forum_ai_creation_version")
public class ForumAiCreationVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("parent_version_id")
    private Long parentVersionId;

    @TableField("artifact_type")
    private String artifactType;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("artifact_json")
    private String artifactJson;

    private Byte selected;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
