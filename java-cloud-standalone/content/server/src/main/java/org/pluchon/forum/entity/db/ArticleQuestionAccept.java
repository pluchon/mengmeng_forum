package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 问答帖采纳记录：一条帖子可采纳多条一级回答或楼中楼
@Data
@TableName("article_question_accept")
public class ArticleQuestionAccept {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 问答帖 ID
    private Long articleId;

    // 一级回答 ID 与 subReplyId 互斥
    private Long replyId;

    // 楼中楼 ID 与 replyId 互斥
    private Long subReplyId;

    private Date createTime;

    private Date updateTime;

    // 逻辑删除：0 正常 1 已删除
    private Integer deleteState;
}
