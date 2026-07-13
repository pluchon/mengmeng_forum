package org.example.forumdemo.service.impl.growth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.common.enums.GrowthExperienceSourceType;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.GrowthLevelPolicy;
import org.example.forumdemo.converter.growth.GrowthExperienceConverter;
import org.example.forumdemo.entity.db.GrowthExperienceLog;
import org.example.forumdemo.entity.db.UserGrowthProfile;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.growth.GrowthExperienceRecordVO;
import org.example.forumdemo.mapper.GrowthExperienceLogMapper;
import org.example.forumdemo.mapper.UserGrowthProfileMapper;
import org.example.forumdemo.service.interfaces.growth.GrowthExperienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 成长经验发放与流水查询
@Service
public class GrowthExperienceServiceImpl implements GrowthExperienceService {

    private static final int DEFAULT_RECORD_PAGE_SIZE = 5;

    private static final int MAX_RECORD_PAGE_SIZE = 20;

    @Autowired
    private GrowthExperienceLogMapper experienceLogMapper;

    @Autowired
    private UserGrowthProfileMapper profileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean grantExperience(
            Long userId,
            GrowthExperienceSourceType sourceType,
            Long sourceBusinessId,
            Integer experience,
            String remark) {
        validateGrant(userId, sourceType, sourceBusinessId, experience);
        if (hasGranted(userId, sourceType, sourceBusinessId)) {
            return false;
        }

        GrowthExperienceLog log = new GrowthExperienceLog();
        log.setUserId(userId);
        log.setSourceType(sourceType.name());
        log.setSourceBusinessId(sourceBusinessId);
        log.setExperienceDelta(experience);
        log.setRemark(remark);
        log.setDeleteState((byte) 0);
        try {
            experienceLogMapper.insert(log);
        } catch (DuplicateKeyException exception) {
            return false;
        }

        UserGrowthProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserGrowthProfile>()
                        .eq(UserGrowthProfile::getUserId, userId)
                        .eq(UserGrowthProfile::getDeleteState, (byte) 0)
                        .last("FOR UPDATE"));
        if (profile == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "成长档案不存在"));
        }

        int totalExperience = Math.max(0, profile.getExperience() == null ? 0 : profile.getExperience()) + experience;
        profile.setExperience(totalExperience);
        profile.setGrowthLevel(GrowthLevelPolicy.calculateLevel(totalExperience));
        profileMapper.updateById(profile);
        return true;
    }

    @Override
    public PageResult<GrowthExperienceRecordVO> recordPage(Long userId, Integer pageNum, Integer pageSize) {
        int validPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int requestedPageSize = pageSize == null ? DEFAULT_RECORD_PAGE_SIZE : pageSize;
        int validPageSize = Math.min(MAX_RECORD_PAGE_SIZE, Math.max(1, requestedPageSize));
        Page<GrowthExperienceLog> page = new Page<>(validPageNum, validPageSize);
        Page<GrowthExperienceLog> result = experienceLogMapper.selectPage(
                page,
                new LambdaQueryWrapper<GrowthExperienceLog>()
                        .eq(GrowthExperienceLog::getUserId, userId)
                        .eq(GrowthExperienceLog::getDeleteState, (byte) 0)
                        .orderByDesc(GrowthExperienceLog::getCreateTime)
                        .orderByDesc(GrowthExperienceLog::getId));
        List<GrowthExperienceRecordVO> records = result.getRecords().stream()
                .map(GrowthExperienceConverter::toVO)
                .toList();
        return new PageResult<>(
                records,
                result.getTotal(),
                validPageNum,
                validPageSize,
                result.getPages(),
                result.hasNext());
    }

    private boolean hasGranted(Long userId, GrowthExperienceSourceType sourceType, Long sourceBusinessId) {
        return experienceLogMapper.selectCount(
                new LambdaQueryWrapper<GrowthExperienceLog>()
                        .eq(GrowthExperienceLog::getUserId, userId)
                        .eq(GrowthExperienceLog::getSourceType, sourceType.name())
                        .eq(GrowthExperienceLog::getSourceBusinessId, sourceBusinessId)) > 0;
    }

    private void validateGrant(
            Long userId,
            GrowthExperienceSourceType sourceType,
            Long sourceBusinessId,
            Integer experience) {
        if (userId == null || userId <= 0
                || sourceType == null
                || sourceBusinessId == null || sourceBusinessId <= 0
                || experience == null || experience <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }
}
