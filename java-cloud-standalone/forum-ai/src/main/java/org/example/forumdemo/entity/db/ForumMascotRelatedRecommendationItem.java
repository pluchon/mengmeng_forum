package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 看板娘相关帖子检索结果项
@Data
@TableName("forum_mascot_related_recommendation_item")
public class ForumMascotRelatedRecommendationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("recommendation_id")
    private Long recommendationId;

    @TableField("article_id")
    private Long articleId;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("selection_reason")
    private String selectionReason;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
