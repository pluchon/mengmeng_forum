package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// IM域AI异步任务
@Data
@TableName("im_ai_task")
public class ImAiTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Byte taskType;
    private Byte targetType;
    private Long targetId;
    private String contentHash;
    private Long triggerUserId;
    private Byte status;
    private Integer retryCount;
    private String resultCode;
    private String resultReason;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
