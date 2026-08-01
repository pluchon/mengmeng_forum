package org.pluchon.forum.service.interfaces.growth;

import org.pluchon.forum.common.enums.GrowthExperienceSourceType;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.growth.GrowthExperienceRecordVO;

public interface GrowthExperienceService {
    boolean grantExperience(
            Long userId,
            GrowthExperienceSourceType sourceType,
            Long sourceBusinessId,
            Integer experience,
            String remark);

    PageResult<GrowthExperienceRecordVO> recordPage(Long userId, Integer pageNum, Integer pageSize);
}
