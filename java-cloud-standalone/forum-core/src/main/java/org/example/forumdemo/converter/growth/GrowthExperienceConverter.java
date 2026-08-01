package org.example.forumdemo.converter.growth;

import org.example.forumdemo.common.enums.GrowthExperienceSourceType;
import org.example.forumdemo.entity.db.GrowthExperienceLog;
import org.example.forumdemo.entity.vo.growth.GrowthExperienceRecordVO;

// 成长经验记录响应转换
public final class GrowthExperienceConverter {

    private GrowthExperienceConverter() {
    }

    // 转换成长经验记录
    public static GrowthExperienceRecordVO toVO(GrowthExperienceLog log) {
        GrowthExperienceRecordVO vo = new GrowthExperienceRecordVO();
        vo.setId(log.getId());
        vo.setSourceType(log.getSourceType());
        vo.setSourceLabel(resolveSourceLabel(log.getSourceType()));
        vo.setExperienceDelta(log.getExperienceDelta());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private static String resolveSourceLabel(String sourceType) {
        try {
            return GrowthExperienceSourceType.valueOf(sourceType).getLabel();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return "成长奖励";
        }
    }
}
