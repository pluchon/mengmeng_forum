package org.pluchon.forum.entity.vo.ai;

import lombok.Data;
import org.pluchon.forum.entity.dto.AiModelUsageDTO;

import java.util.List;

// AI 帖子标签推荐结果
@Data
public class AiHubArticleTagRecommendResultVO {

    // 推荐的已有标签主键
    private List<Long> tagIds;

    // 本次文章摘要
    private String summary;

    // 是否升级过深度模型
    private Boolean deepUsed;

    // 模型调用用量
    private List<AiModelUsageDTO> usageItems;
}
