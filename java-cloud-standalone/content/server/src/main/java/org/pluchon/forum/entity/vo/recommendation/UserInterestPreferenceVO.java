package org.pluchon.forum.entity.vo.recommendation;

import lombok.Data;

import java.util.List;

// 当前用户的推荐兴趣设置
@Data
public class UserInterestPreferenceVO {

    // 是否启用个性化推荐
    private Boolean personalizedEnabled;

    // 已选择的细分板块ID
    private List<Long> boardIds;
}
