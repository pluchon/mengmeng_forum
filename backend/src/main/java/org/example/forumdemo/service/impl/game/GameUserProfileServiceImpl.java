package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.converter.GameConverter;
import org.example.forumdemo.entity.db.GameGobangMatchRecord;
import org.example.forumdemo.entity.db.GameGobangRoomMove;
import org.example.forumdemo.entity.db.GameJinziMatchRecord;
import org.example.forumdemo.entity.db.GameJinziRoomMove;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.game.GameMatchRecordVO;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.entity.vo.game.GobangMoveVO;
import org.example.forumdemo.entity.vo.game.GobangReplayVO;
import org.example.forumdemo.mapper.GameGobangMatchRecordMapper;
import org.example.forumdemo.mapper.GameGobangRoomMoveMapper;
import org.example.forumdemo.mapper.GameJinziMatchRecordMapper;
import org.example.forumdemo.mapper.GameJinziRoomMoveMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 游戏用户资料服务，负责按用户和游戏初始化独立战绩
@Service
public class GameUserProfileServiceImpl implements GameUserProfileService {

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameGobangMatchRecordMapper gameGobangMatchRecordMapper;

    @Autowired
    private GameJinziMatchRecordMapper gameJinziMatchRecordMapper;

    @Autowired
    private GameGobangRoomMoveMapper gameGobangRoomMoveMapper;

    @Autowired
    private GameJinziRoomMoveMapper gameJinziRoomMoveMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameUserProfile getOrCreateProfile(Long userId, String gameCode) {
        if (userId == null || gameCode == null || gameCode.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GameUserProfile existing = selectProfile(userId, gameCode);
        if (existing != null) {
            return existing;
        }
        GameUserProfile profile = new GameUserProfile();
        profile.setUserId(userId);
        profile.setGameCode(gameCode);
        profile.setScore(GameConstants.INITIAL_SCORE);
        profile.setTotalCount(0);
        profile.setWinCount(0);
        profile.setLoseCount(0);
        profile.setDrawCount(0);
        profile.setCurrentStatus(GameConstants.PROFILE_IDLE);
        profile.setDeleteState((byte) 0);
        try {
            gameUserProfileMapper.insert(profile);
        } catch (Exception e) {
            // 并发首次进入时唯一键可能已由另一个请求插入，回查即可。
            GameUserProfile concurrent = selectProfile(userId, gameCode);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
        return profile;
    }

    @Override
    public GameUserProfileVO getProfileVO(Long userId, String gameCode) {
        GameUserProfile profile = getOrCreateProfile(userId, gameCode);
        User user = userMapper.selectById(userId);
        return GameConverter.toProfileVO(profile, user);
    }

    @Override
    public PageResult<GameMatchRecordVO> listGobangRecords(Long userId, Integer pageNum, Integer pageSize) {
        return listMatchRecords(userId, pageNum, pageSize, gameGobangMatchRecordMapper);
    }

    @Override
    public PageResult<GameMatchRecordVO> listJinziRecords(Long userId, Integer pageNum, Integer pageSize) {
        return listMatchRecords(userId, pageNum, pageSize, gameJinziMatchRecordMapper);
    }

    private PageResult<GameMatchRecordVO> listGobangMatchRecords(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            Page<GameGobangMatchRecord> result
    ) {
        List<GameMatchRecordVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameGobangMatchRecord record : result.getRecords()) {
            rows.add(GameConverter.toGobangRecordVO(record));
        }
        return new PageResult<>(
                rows,
                result.getTotal(),
                pageNum,
                pageSize,
                result.getPages(),
                result.hasNext()
        );
    }

    private PageResult<GameMatchRecordVO> listJinziMatchRecords(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            Page<GameJinziMatchRecord> result
    ) {
        List<GameMatchRecordVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameJinziMatchRecord record : result.getRecords()) {
            rows.add(GameConverter.toJinziRecordVO(record));
        }
        return new PageResult<>(
                rows,
                result.getTotal(),
                pageNum,
                pageSize,
                result.getPages(),
                result.hasNext()
        );
    }

    private PageResult<GameMatchRecordVO> listMatchRecords(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            GameGobangMatchRecordMapper mapper
    ) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameGobangMatchRecord> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<GameGobangMatchRecord> wrapper = new LambdaQueryWrapper<GameGobangMatchRecord>()
                .eq(GameGobangMatchRecord::getDeleteState, (byte) 0)
                .and(q -> q.eq(GameGobangMatchRecord::getBlackUserId, userId)
                        .or()
                        .eq(GameGobangMatchRecord::getWhiteUserId, userId))
                .orderByDesc(GameGobangMatchRecord::getEndedAt)
                .orderByDesc(GameGobangMatchRecord::getId);
        Page<GameGobangMatchRecord> result = mapper.selectPage(page, wrapper);
        return listGobangMatchRecords(userId, validPageNum, validPageSize, result);
    }

    private PageResult<GameMatchRecordVO> listMatchRecords(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            GameJinziMatchRecordMapper mapper
    ) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameJinziMatchRecord> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<GameJinziMatchRecord> wrapper = new LambdaQueryWrapper<GameJinziMatchRecord>()
                .eq(GameJinziMatchRecord::getDeleteState, (byte) 0)
                .and(q -> q.eq(GameJinziMatchRecord::getBlackUserId, userId)
                        .or()
                        .eq(GameJinziMatchRecord::getWhiteUserId, userId))
                .orderByDesc(GameJinziMatchRecord::getEndedAt)
                .orderByDesc(GameJinziMatchRecord::getId);
        Page<GameJinziMatchRecord> result = mapper.selectPage(page, wrapper);
        return listJinziMatchRecords(userId, validPageNum, validPageSize, result);
    }

    @Override
    public List<GameUserProfileVO> listLeaderboard(String gameCode, Integer pageSize) {
        if (gameCode == null || gameCode.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameUserProfile> page = new Page<>(1, validPageSize);
        LambdaQueryWrapper<GameUserProfile> wrapper = new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, gameCode)
                .eq(GameUserProfile::getDeleteState, (byte) 0)
                .orderByDesc(GameUserProfile::getScore)
                .orderByDesc(GameUserProfile::getWinCount)
                .orderByDesc(GameUserProfile::getTotalCount)
                .orderByAsc(GameUserProfile::getId);
        Page<GameUserProfile> result = gameUserProfileMapper.selectPage(page, wrapper);
        List<GameUserProfileVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameUserProfile profile : result.getRecords()) {
            rows.add(GameConverter.toProfileVO(profile, userMapper.selectById(profile.getUserId())));
        }
        return rows;
    }

    @Override
    public GobangReplayVO getGobangReplay(Long userId, Long recordId) {
        if (userId == null || recordId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GameGobangMatchRecord record = gameGobangMatchRecordMapper.selectById(recordId);
        if (record == null
                || record.getDeleteState() == null
                || record.getDeleteState() != 0
                || (!userId.equals(record.getBlackUserId()) && !userId.equals(record.getWhiteUserId()))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        List<GameGobangRoomMove> moves = gameGobangRoomMoveMapper.selectList(new LambdaQueryWrapper<GameGobangRoomMove>()
                .eq(GameGobangRoomMove::getRoomId, record.getRoomId())
                .eq(GameGobangRoomMove::getDeleteState, (byte) 0)
                .orderByAsc(GameGobangRoomMove::getMoveNo)
                .orderByAsc(GameGobangRoomMove::getId));
        List<GobangMoveVO> moveRows = new ArrayList<>(moves.size());
        for (GameGobangRoomMove move : moves) {
            moveRows.add(GameConverter.toGobangMoveVO(move));
        }
        return new GobangReplayVO(GameConverter.toGobangRecordVO(record), moveRows);
    }

    @Override
    public GobangReplayVO getJinziReplay(Long userId, Long recordId) {
        if (userId == null || recordId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GameJinziMatchRecord record = gameJinziMatchRecordMapper.selectById(recordId);
        if (record == null
                || record.getDeleteState() == null
                || record.getDeleteState() != 0
                || (!userId.equals(record.getBlackUserId()) && !userId.equals(record.getWhiteUserId()))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        List<GameJinziRoomMove> moves = gameJinziRoomMoveMapper.selectList(new LambdaQueryWrapper<GameJinziRoomMove>()
                .eq(GameJinziRoomMove::getRoomId, record.getRoomId())
                .eq(GameJinziRoomMove::getDeleteState, (byte) 0)
                .orderByAsc(GameJinziRoomMove::getMoveNo)
                .orderByAsc(GameJinziRoomMove::getId));
        List<GobangMoveVO> moveRows = new ArrayList<>(moves.size());
        for (GameJinziRoomMove move : moves) {
            moveRows.add(GameConverter.toJinziMoveVO(move));
        }
        return new GobangReplayVO(GameConverter.toJinziRecordVO(record), moveRows);
    }

    @Override
    public void updateStatus(Long userId, String gameCode, String status, String roomId) {
        getOrCreateProfile(userId, gameCode);
        gameUserProfileMapper.updatePlayStatus(userId, gameCode, status, roomId);
    }

    private GameUserProfile selectProfile(Long userId, String gameCode) {
        List<GameUserProfile> rows = gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getUserId, userId)
                .eq(GameUserProfile::getGameCode, gameCode)
                .eq(GameUserProfile::getDeleteState, (byte) 0));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
