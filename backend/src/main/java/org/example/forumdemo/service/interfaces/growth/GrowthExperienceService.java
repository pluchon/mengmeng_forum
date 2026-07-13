package org.example.forumdemo.service.interfaces.growth;

import org.example.forumdemo.common.enums.GrowthExperienceSourceType;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.growth.GrowthExperienceRecordVO;

public interface GrowthExperienceService {
    boolean grantExperience(
            Long userId,
            GrowthExperienceSourceType sourceType,
            Long sourceBusinessId,
            Integer experience,
            String remark);

    PageResult<GrowthExperienceRecordVO> recordPage(Long userId, Integer pageNum, Integer pageSize);
}
