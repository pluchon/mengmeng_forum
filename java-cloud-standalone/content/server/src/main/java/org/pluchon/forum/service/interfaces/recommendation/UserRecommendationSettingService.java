package org.pluchon.forum.service.interfaces.recommendation;

import org.pluchon.forum.entity.dto.recommendation.UpdateRecommendationSettingRequest;
import org.pluchon.forum.entity.vo.recommendation.UserRecommendationSettingVO;

import java.util.List;
import java.util.Set;

// 用户推荐显示设置服务
public interface UserRecommendationSettingService {

    UserRecommendationSettingVO getCurrentSetting(Long userId);

    void updateSetting(Long userId, UpdateRecommendationSettingRequest request);

    boolean isPersonalizedEnabled(Long userId);

    Set<Long> getInterestBoardIds(Long userId);

    List<String> getInterestBoardNames(Long userId);
}
