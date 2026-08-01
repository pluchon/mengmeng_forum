package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("forum_article_tag_link")
public class ForumArticleTagLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long tagId;
    private Date createTime;
}
