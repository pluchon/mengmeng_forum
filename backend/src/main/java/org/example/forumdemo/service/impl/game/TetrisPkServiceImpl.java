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
    public List<GameUserProfileVO> listLeaderboard(Integer pageSize) {
        int validPageSize = PageUtils.getValidPageSize(pageSize == null ? 20 : pageSize);
        Page<GameUserProfile> page = new Page<>(1, validPageSize);
        LambdaQueryWrapper<GameUserProfile> wrapper = new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, GameConstants.TETRIS_PK)
                .eq(GameUserProfile::getDeleteState, (byte) 0)
                .orderByDesc(GameUserProfile::getScore)
                .orderByDesc(GameUserProfile::getWinCount);
        Page<GameUserProfile> result = gameUserProfileMapper.selectPage(page, wrapper);
        List<Long> userIds = result.getRecords().stream().map(GameUserProfile::getUserId).toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
        }
        List<GameUserProfileVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameUserProfile profile : result.getRecords()) {
            rows.add(GameConverter.toProfileVO(profile, userMap.get(profile.getUserId())));
        }
        return rows;
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
}
