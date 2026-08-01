package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("forum_article_tag_request")
public class ForumArticleTagRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long boardId;
    private Long categoryId;
    private String proposedName;
    private Byte status;
    private String auditMessage;
    private Long approvedTagId;
    private Byte deleteState;
    private Date createTime;
    private Date updateTime;
}
