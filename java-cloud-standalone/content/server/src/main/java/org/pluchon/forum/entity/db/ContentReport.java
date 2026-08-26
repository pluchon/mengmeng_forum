package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 帖子与评论举报记录
@Data
@TableName("content_report")
public class ContentReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterUserId;
    private Byte targetType;
    private Long targetId;
    private String contentHash;
    private String reason;
    private String taskId;
    private Byte resultStatus;
    private String resultMessage;
    private Date createTime;
    private Date updateTime;
    private Byte deleteState;
}
