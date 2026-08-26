package org.pluchon.forum.entity.vo.recommendation;

import lombok.Data;

import java.util.List;

// 用户个性化推荐设置
@Data
public class UserRecommendationSettingVO {

    // 是否启用个性化推荐
    private Boolean personalizedEnabled;

    // 手选兴趣版块 ID
    private List<Long> interestBoardIds;
}
