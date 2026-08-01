package org.pluchon.forum.service.interfaces.growth;

import org.pluchon.forum.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.growth.GrowthChallengeDetailVO;
import org.pluchon.forum.entity.vo.growth.GrowthChallengeVO;
import org.pluchon.forum.entity.vo.growth.GrowthExperienceRecordVO;
import org.pluchon.forum.entity.vo.growth.GrowthOverviewVO;
import org.pluchon.forum.entity.vo.growth.GrowthSubmitResultVO;

public interface GrowthService {
    GrowthOverviewVO overview(Long userId);

    PageResult<GrowthChallengeVO> challengePage(Long userId, Integer pageNum, Integer pageSize);

    PageResult<GrowthExperienceRecordVO> experienceRecordPage(Long userId, Integer pageNum, Integer pageSize);

    GrowthChallengeDetailVO start(Long userId, String challengeCode);

    GrowthSubmitResultVO submit(Long userId, String challengeCode, GrowthChallengeSubmitRequest request);

    void createNewUserProfile(Long userId);

    void requireFormalUser(Long userId);
}
