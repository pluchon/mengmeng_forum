package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 一级评论点赞记录
@Data
@TableName("article_reply_like")
public class ArticleReplyLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 点赞用户
    private Long userId;

    // 一级评论 ID
    private Long replyId;

    private Date createTime;
}
