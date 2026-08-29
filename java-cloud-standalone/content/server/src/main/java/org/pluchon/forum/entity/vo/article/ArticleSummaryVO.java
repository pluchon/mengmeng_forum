package org.pluchon.forum.entity.vo.article;

import lombok.Data;

// 帖子AI总结状态与内容
@Data
public class ArticleSummaryVO {

    private String status;

    private String summary;

    private Boolean canExpand;

    private Boolean canRegenerate;

    // 本次"重新生成"是否落在冷却期内而被忽略。
    // 之前冷却命中时静默返回当前状态，前端照常开始轮询，用户以为在重新生成
    private Boolean cooldownHit;
}
