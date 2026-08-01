package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// AI 会员长期偏好记忆
@Data
@TableName("forum_ai_long_term_memory")
public class ForumAiLongTermMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("source_session_id")
    private Long sourceSessionId;

    @TableField("memory_type")
    private String memoryType;

    private String content;

    private Byte enabled;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
