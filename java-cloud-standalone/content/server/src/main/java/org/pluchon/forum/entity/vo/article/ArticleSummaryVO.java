package org.pluchon.forum.entity.vo.article;

import lombok.Data;

// 帖子AI总结状态与内容
@Data
public class ArticleSummaryVO {

    private String status;

    private String summary;

    private Boolean canExpand;

    private Boolean canRegenerate;
}
