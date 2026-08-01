package org.pluchon.forum.service.interfaces.recommendation;

import org.pluchon.forum.entity.dto.recommendation.SaveInterestPreferenceRequest;
import org.pluchon.forum.entity.vo.recommendation.UserInterestPreferenceVO;

import java.util.Set;

// 用户推荐兴趣设置服务
public interface UserInterestPreferenceService {

    UserInterestPreferenceVO getPreferences(Long userId);

    void savePreferences(Long userId, SaveInterestPreferenceRequest request);

    void resetPreferences(Long userId);

    boolean isPersonalizationEnabled(Long userId);

    Set<Long> listActiveBoardIds(Long userId);
}
