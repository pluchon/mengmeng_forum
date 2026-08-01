package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 帖子推荐特征快照，仅保存公开内容的派生结果
@Data
@TableName("forum_article_ai_feature")
public class ForumArticleAiFeature {

    // 主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 帖子ID
    private Long articleId;

    // 结构化特征 JSON
    private String featureJson;

    // 特征协议版本
    private String featureVersion;

    // 内容摘要指纹
    private String contentHash;

    // 特征生成来源
    private String generatedBy;

    // 逻辑删除状态
    private Byte deleteState;

    // 创建时间
    private Date createTime;

    // 更新时间
    private Date updateTime;
}
