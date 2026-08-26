package org.pluchon.forum.entity.dto.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

// 更新个性化推荐设置请求
@Data
public class UpdateRecommendationSettingRequest {

    // 是否启用个性化推荐
    @NotNull(message = "个性化推荐开关不能为空")
    private Boolean personalizedEnabled;

    // 手选兴趣版块，最多 5 个；null 表示不改动
    @Size(max = 5, message = "兴趣版块最多选择5个")
    private List<Long> interestBoardIds;
}
