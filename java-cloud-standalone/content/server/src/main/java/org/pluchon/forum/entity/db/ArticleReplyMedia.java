package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("article_reply_media")
public class ArticleReplyMedia {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long replyId;
    private Long subReplyId;
    private Byte mediaType;
    private String mediaUrl;
    private Long shopId;
    private Integer sortOrder;
    private Date createTime;
}
