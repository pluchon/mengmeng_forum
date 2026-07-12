package org.example.forumdemo.service.impl.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.forumdemo.common.enums.PersonalizationState;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.db.UserInterestPreference;
import org.example.forumdemo.entity.db.UserRecommendFeedback;
import org.example.forumdemo.entity.dto.recommendation.SaveInterestPreferenceRequest;
import org.example.forumdemo.entity.vo.recommendation.UserInterestPreferenceVO;
import org.example.forumdemo.mapper.BoardMapper;
import org.example.forumdemo.mapper.UserInterestPreferenceMapper;
import org.example.forumdemo.mapper.UserRecommendFeedbackMapper;
import org.example.forumdemo.service.interfaces.recommendation.UserInterestPreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 用户推荐兴趣设置服务实现
@Service
public class UserInterestPreferenceServiceImpl implements UserInterestPreferenceService {

    private static final long SETTING_BOARD_ID = 0L;
    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;
    private static final byte BOARD_ENABLED = 0;

    @Autowired
    private UserInterestPreferenceMapper preferenceMapper;

    @Autowired
    private UserRecommendFeedbackMapper feedbackMapper;

    @Autowired
    private BoardMapper boardMapper;

    @Override
    public UserInterestPreferenceVO getPreferences(Long userId) {
        requireUserId(userId);
        List<UserInterestPreference> records = preferenceMapper.selectList(new LambdaQueryWrapper<UserInterestPreference>()
                .eq(UserInterestPreference::getUserId, userId)
                .eq(UserInterestPreference::getDeleteState, DELETE_FALSE));
        UserInterestPreferenceVO result = new UserInterestPreferenceVO();
        result.setPersonalizedEnabled(records.stream()
                .filter(item -> SETTING_BOARD_ID == item.getBoardId())
                .findFirst()
                .map(item -> PersonalizationState.isEnabled(item.getPersonalizedEnabled()))
                .orElse(true));
        result.setBoardIds(records.stream()
                .map(UserInterestPreference::getBoardId)
                .filter(boardId -> boardId != null && boardId > SETTING_BOARD_ID)
                .sorted()
                .toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePreferences(Long userId, SaveInterestPreferenceRequest request) {
        requireUserId(userId);
        if (request == null || request.getPersonalizedEnabled() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Set<Long> selectedBoardIds = normalizeBoardIds(request.getBoardIds());
        validateBoards(selectedBoardIds);
        Map<Long, UserInterestPreference> recordsByBoard = loadAllByBoard(userId);
        upsertPreference(recordsByBoard.get(SETTING_BOARD_ID), userId, SETTING_BOARD_ID,
                request.getPersonalizedEnabled() ? PersonalizationState.ENABLED.getCode() : PersonalizationState.DISABLED.getCode());

        for (Map.Entry<Long, UserInterestPreference> entry : recordsByBoard.entrySet()) {
            Long boardId = entry.getKey();
            if (boardId != null && boardId > SETTING_BOARD_ID && !selectedBoardIds.contains(boardId)) {
                preferenceMapper.update(null, new LambdaUpdateWrapper<UserInterestPreference>()
                        .eq(UserInterestPreference::getId, entry.getValue().getId())
                        .eq(UserInterestPreference::getDeleteState, DELETE_FALSE)
                        .set(UserInterestPreference::getDeleteState, DELETE_TRUE));
            }
        }
        for (Long boardId : selectedBoardIds) {
            upsertPreference(recordsByBoard.get(boardId), userId, boardId,
                    request.getPersonalizedEnabled() ? PersonalizationState.ENABLED.getCode() : PersonalizationState.DISABLED.getCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPreferences(Long userId) {
        requireUserId(userId);
        preferenceMapper.update(null, new LambdaUpdateWrapper<UserInterestPreference>()
                .eq(UserInterestPreference::getUserId, userId)
                .eq(UserInterestPreference::getDeleteState, DELETE_FALSE)
                .set(UserInterestPreference::getDeleteState, DELETE_TRUE));
        feedbackMapper.update(null, new LambdaUpdateWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, userId)
                .eq(UserRecommendFeedback::getDeleteState, DELETE_FALSE)
                .set(UserRecommendFeedback::getDeleteState, DELETE_TRUE));
    }

    @Override
    public boolean isPersonalizationEnabled(Long userId) {
        return getPreferences(userId).getPersonalizedEnabled();
    }

    @Override
    public Set<Long> listActiveBoardIds(Long userId) {
        return new HashSet<>(getPreferences(userId).getBoardIds());
    }

    private Map<Long, UserInterestPreference> loadAllByBoard(Long userId) {
        List<UserInterestPreference> records = preferenceMapper.selectList(new LambdaQueryWrapper<UserInterestPreference>()
                .eq(UserInterestPreference::getUserId, userId));
        Map<Long, UserInterestPreference> result = new HashMap<>();
        for (UserInterestPreference record : records) {
            if (record.getBoardId() != null) {
                result.put(record.getBoardId(), record);
            }
        }
        return result;
    }

    private void upsertPreference(UserInterestPreference existing, Long userId, Long boardId, byte state) {
        if (existing == null) {
            UserInterestPreference record = new UserInterestPreference();
            record.setUserId(userId);
            record.setBoardId(boardId);
            record.setPersonalizedEnabled(state);
            record.setDeleteState(DELETE_FALSE);
            preferenceMapper.insert(record);
            return;
        }
        preferenceMapper.update(null, new LambdaUpdateWrapper<UserInterestPreference>()
                .eq(UserInterestPreference::getId, existing.getId())
                .set(UserInterestPreference::getPersonalizedEnabled, state)
                .set(UserInterestPreference::getDeleteState, DELETE_FALSE));
    }

    private Set<Long> normalizeBoardIds(Collection<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>(boardIds);
        if (result.size() != boardIds.size() || result.size() > 8 || result.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return result;
    }

    private void validateBoards(Set<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return;
        }
        List<Board> boards = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                .in(Board::getId, boardIds)
                .eq(Board::getState, BOARD_ENABLED)
                .eq(Board::getDeleteState, DELETE_FALSE));
        if (boards.size() != boardIds.size()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
    }
}
