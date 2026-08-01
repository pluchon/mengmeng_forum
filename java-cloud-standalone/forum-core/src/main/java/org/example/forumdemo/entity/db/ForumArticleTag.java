package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("forum_article_tag")
public class ForumArticleTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String colorKey;
    private Byte scopeType;
    private Long scopeId;
    private Integer sort;
    private Byte state;
    private Byte deleteState;
    private Date createTime;
    private Date updateTime;
}
