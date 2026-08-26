package org.pluchon.forum.service.impl.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.converter.RecommendationSettingConverter;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.UserRecommendationSetting;
import org.pluchon.forum.entity.dto.recommendation.UpdateRecommendationSettingRequest;
import org.pluchon.forum.entity.vo.recommendation.UserRecommendationSettingVO;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.UserRecommendationSettingMapper;
import org.pluchon.forum.service.interfaces.recommendation.RecommendationAiProfileService;
import org.pluchon.forum.service.interfaces.recommendation.UserRecommendationSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 用户个性化推荐开关与兴趣版块实现
@Service
public class UserRecommendationSettingServiceImpl implements UserRecommendationSettingService {

    private static final byte DELETE_FALSE = 0;
    private static final byte ENABLED = 1;
    private static final byte DISABLED = 0;
    private static final byte STATE_ENABLED = 0;

    @Autowired
    private UserRecommendationSettingMapper userRecommendationSettingMapper;

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    @Lazy
    private RecommendationAiProfileService recommendationAiProfileService;

    @Override
    public UserRecommendationSettingVO getCurrentSetting(Long userId) {
        requireUserId(userId);
        return RecommendationSettingConverter.toVO(findActiveSetting(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSetting(Long userId, UpdateRecommendationSettingRequest request) {
        requireUserId(userId);
        if (request == null || request.getPersonalizedEnabled() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        byte nextState = Boolean.TRUE.equals(request.getPersonalizedEnabled()) ? ENABLED : DISABLED;
        boolean updateInterest = request.getInterestBoardIds() != null;
        List<Long> nextInterestIds = updateInterest
                ? validateInterestBoardIds(request.getInterestBoardIds())
                : null;
        String nextInterestJson = updateInterest
                ? RecommendationSettingConverter.serializeInterestBoardIds(nextInterestIds)
                : null;
        UserRecommendationSetting existing = findActiveSetting(userId);
        boolean interestChanged = false;
        if (existing == null) {
            UserRecommendationSetting created = new UserRecommendationSetting();
            created.setUserId(userId);
            created.setPersonalizedEnabled(nextState);
            created.setInterestBoardIds(updateInterest ? nextInterestJson : null);
            created.setDeleteState(DELETE_FALSE);
            userRecommendationSettingMapper.insert(created);
            interestChanged = updateInterest && nextInterestIds != null && !nextInterestIds.isEmpty();
        } else {
            List<Long> previousInterest = RecommendationSettingConverter.parseInterestBoardIds(existing.getInterestBoardIds());
            LambdaUpdateWrapper<UserRecommendationSetting> update = new LambdaUpdateWrapper<UserRecommendationSetting>()
                    .eq(UserRecommendationSetting::getId, existing.getId())
                    .set(UserRecommendationSetting::getPersonalizedEnabled, nextState);
            if (updateInterest) {
                update.set(UserRecommendationSetting::getInterestBoardIds, nextInterestJson);
                interestChanged = !Objects.equals(previousInterest, nextInterestIds);
            }
            userRecommendationSettingMapper.update(null, update);
        }
        if (interestChanged) {
            recommendationAiProfileService.requestProfileRefresh(userId);
        }
    }

    @Override
    public boolean isPersonalizedEnabled(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        UserRecommendationSetting setting = findActiveSetting(userId);
        return setting == null || ENABLED == setting.getPersonalizedEnabled();
    }

    @Override
    public Set<Long> getInterestBoardIds(Long userId) {
        if (userId == null || userId <= 0) {
            return Set.of();
        }
        UserRecommendationSetting setting = findActiveSetting(userId);
        if (setting == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(RecommendationSettingConverter.parseInterestBoardIds(setting.getInterestBoardIds()));
    }

    @Override
    public List<String> getInterestBoardNames(Long userId) {
        Set<Long> boardIds = getInterestBoardIds(userId);
        if (boardIds.isEmpty()) {
            return List.of();
        }
        return boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, boardIds)
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED)
                        .select(Board::getId, Board::getName))
                .stream()
                .map(Board::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> validateInterestBoardIds(List<Long> boardIds) {
        List<Long> normalized = RecommendationSettingConverter.normalizeInterestBoardIds(boardIds);
        if (normalized.isEmpty()) {
            return List.of();
        }
        Set<Long> existing = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                        .in(Board::getId, normalized)
                        .eq(Board::getDeleteState, DELETE_FALSE)
                        .eq(Board::getState, STATE_ENABLED)
                        .select(Board::getId))
                .stream()
                .map(Board::getId)
                .collect(Collectors.toSet());
        if (existing.size() != normalized.size()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return normalized;
    }

    private UserRecommendationSetting findActiveSetting(Long userId) {
        return userRecommendationSettingMapper.selectOne(new LambdaQueryWrapper<UserRecommendationSetting>()
                .eq(UserRecommendationSetting::getUserId, userId)
                .eq(UserRecommendationSetting::getDeleteState, DELETE_FALSE));
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
    }
}
