package org.example.forumdemo.service.interfaces.growth;

import org.example.forumdemo.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeDetailVO;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeVO;
import org.example.forumdemo.entity.vo.growth.GrowthExperienceRecordVO;
import org.example.forumdemo.entity.vo.growth.GrowthOverviewVO;
import org.example.forumdemo.entity.vo.growth.GrowthSubmitResultVO;

public interface GrowthService {
    GrowthOverviewVO overview(Long userId);

    PageResult<GrowthChallengeVO> challengePage(Long userId, Integer pageNum, Integer pageSize);

    PageResult<GrowthExperienceRecordVO> experienceRecordPage(Long userId, Integer pageNum, Integer pageSize);

    GrowthChallengeDetailVO start(Long userId, String challengeCode);

    GrowthSubmitResultVO submit(Long userId, String challengeCode, GrowthChallengeSubmitRequest request);

    void createNewUserProfile(Long userId);

    void requireFormalUser(Long userId);
}
