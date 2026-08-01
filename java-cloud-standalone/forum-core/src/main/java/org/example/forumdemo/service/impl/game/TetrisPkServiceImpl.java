package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.converter.GameConverter;
import org.example.forumdemo.converter.TetrisPkConverter;
import org.example.forumdemo.entity.db.GameTetrisPkMatchRecord;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.entity.vo.game.TetrisPkLeaderboardVO;
import org.example.forumdemo.entity.vo.game.TetrisPkRecordVO;
import org.example.forumdemo.entity.vo.game.TetrisPkReplayVO;
import org.example.forumdemo.mapper.GameTetrisPkMatchRecordMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.TetrisPkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 俄罗斯方块 PK 资料、历史与排行榜服务
@Service
public class TetrisPkServiceImpl implements TetrisPkService {

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameTetrisPkMatchRecordMapper gameTetrisPkMatchRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public GameUserProfileVO getProfile(Long userId) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.TETRIS_PK);
        User user = userMapper.selectById(userId);
        return GameConverter.toProfileVO(profile, user);
    }

    @Override
    public PageResult<TetrisPkRecordVO> listRecords(Long userId, Integer pageNum, Integer pageSize) {
        gameUserProfileService.getOrCreateProfile(userId, GameConstants.TETRIS_PK);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameTetrisPkMatchRecord> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<GameTetrisPkMatchRecord> wrapper = new LambdaQueryWrapper<GameTetrisPkMatchRecord>()
                .and(w -> w.eq(GameTetrisPkMatchRecord::getPlayer1UserId, userId)
                        .or()
                        .eq(GameTetrisPkMatchRecord::getPlayer2UserId, userId))
                .eq(GameTetrisPkMatchRecord::getDeleteState, (byte) 0)
                .orderByDesc(GameTetrisPkMatchRecord::getEndedAt)
                .orderByDesc(GameTetrisPkMatchRecord::getId);
        Page<GameTetrisPkMatchRecord> result = gameTetrisPkMatchRecordMapper.selectPage(page, wrapper);
        Map<Long, User> userMap = loadUsers(result.getRecords(), userId);
        List<TetrisPkRecordVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameTetrisPkMatchRecord record : result.getRecords()) {
            Long opponentId = userId.equals(record.getPlayer1UserId())
                    ? record.getPlayer2UserId()
                    : record.getPlayer1UserId();
            User opponent = userMap.get(opponentId);
            String opponentNickname = opponent == null ? null : opponent.getNickname();
            rows.add(TetrisPkConverter.toRecordVO(record, userId, opponentNickname));
        }
        return new PageResult<>(
                rows,
                result.getTotal(),
                validPageNum,
                validPageSize,
                result.getPages(),
                result.hasNext()
        );
    }

    @Override
    public PageResult<TetrisPkLeaderboardVO> listLeaderboard(Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize == null ? 20 : pageSize);
        List<GameUserProfile> profiles = gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, GameConstants.TETRIS_PK)
                .eq(GameUserProfile::getDeleteState, (byte) 0));
        Map<Long, Integer> bestScores = loadBestScores();
        profiles.sort(Comparator
                .comparingInt(this::profileWinRate).reversed()
                .thenComparing(
                        profile -> bestScores.getOrDefault(profile.getUserId(), 0),
                        Comparator.reverseOrder()
                )
                .thenComparing(GameUserProfile::getUserId));

        int total = profiles.size();
        long offset = (long) (validPageNum - 1) * validPageSize;
        int fromIndex = offset >= total ? total : (int) offset;
        int toIndex = Math.min(fromIndex + validPageSize, total);
        List<GameUserProfile> pageProfiles = profiles.subList(fromIndex, toIndex);
        List<Long> userIds = pageProfiles.stream().map(GameUserProfile::getUserId).toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
        }
        List<TetrisPkLeaderboardVO> rows = new ArrayList<>(pageProfiles.size());
        for (GameUserProfile profile : pageProfiles) {
            rows.add(TetrisPkConverter.toLeaderboardVO(
                    profile,
                    userMap.get(profile.getUserId()),
                    bestScores.getOrDefault(profile.getUserId(), 0)
            ));
        }
        long pages = total == 0 ? 0 : (total + validPageSize - 1L) / validPageSize;
        return new PageResult<>(rows, (long) total, validPageNum, validPageSize, pages, toIndex < total);
    }

    @Override
    public TetrisPkReplayVO getReplay(Long userId, Long recordId) {
        GameTetrisPkMatchRecord record = gameTetrisPkMatchRecordMapper.selectById(recordId);
        if (record == null || record.getDeleteState() != null && record.getDeleteState() != 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "记录不存在"));
        }
        if (!userId.equals(record.getPlayer1UserId()) && !userId.equals(record.getPlayer2UserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        Long opponentId = userId.equals(record.getPlayer1UserId())
                ? record.getPlayer2UserId()
                : record.getPlayer1UserId();
        User opponent = userMapper.selectById(opponentId);
        TetrisPkRecordVO recordVO = TetrisPkConverter.toRecordVO(
                record,
                userId,
                opponent == null ? null : opponent.getNickname()
        );
        return new TetrisPkReplayVO(recordVO, record.getReplayPayload());
    }

    private Map<Long, User> loadUsers(List<GameTetrisPkMatchRecord> records, Long viewerUserId) {
        List<Long> userIds = new ArrayList<>();
        for (GameTetrisPkMatchRecord record : records) {
            Long opponentId = viewerUserId.equals(record.getPlayer1UserId())
                    ? record.getPlayer2UserId()
                    : record.getPlayer1UserId();
            if (opponentId != null && !userIds.contains(opponentId)) {
                userIds.add(opponentId);
            }
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
        }
        return userMap;
    }

    private Map<Long, Integer> loadBestScores() {
        List<GameTetrisPkMatchRecord> records = gameTetrisPkMatchRecordMapper.selectList(
                new LambdaQueryWrapper<GameTetrisPkMatchRecord>()
                        .eq(GameTetrisPkMatchRecord::getDeleteState, (byte) 0)
                        .select(
                                GameTetrisPkMatchRecord::getPlayer1UserId,
                                GameTetrisPkMatchRecord::getPlayer2UserId,
                                GameTetrisPkMatchRecord::getPlayer1Score,
                                GameTetrisPkMatchRecord::getPlayer2Score
                        )
        );
        Map<Long, Integer> bestScores = new HashMap<>();
        for (GameTetrisPkMatchRecord record : records) {
            mergeBestScore(bestScores, record.getPlayer1UserId(), record.getPlayer1Score());
            mergeBestScore(bestScores, record.getPlayer2UserId(), record.getPlayer2Score());
        }
        return bestScores;
    }

    private void mergeBestScore(Map<Long, Integer> bestScores, Long userId, Integer score) {
        if (userId == null) {
            return;
        }
        bestScores.merge(userId, score == null ? 0 : score, Math::max);
    }

    private int profileWinRate(GameUserProfile profile) {
        int totalCount = profile.getTotalCount() == null ? 0 : profile.getTotalCount();
        int winCount = profile.getWinCount() == null ? 0 : profile.getWinCount();
        return totalCount <= 0 ? 0 : (int) Math.round(winCount * 100.0 / totalCount);
    }
}
