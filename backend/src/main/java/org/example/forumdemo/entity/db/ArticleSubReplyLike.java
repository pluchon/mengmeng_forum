package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 楼中楼回复点赞记录
@Data
@TableName("article_sub_reply_like")
public class ArticleSubReplyLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 点赞用户
    private Long userId;

    // 楼中楼回复 ID
    private Long subReplyId;

    private Date createTime;
}
