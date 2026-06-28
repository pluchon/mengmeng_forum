package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.constant.GameRedisKeys;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.converter.TetrisConverter;
import org.example.forumdemo.entity.db.GameTetrisRecord;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.game.TetrisSettleRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.game.TetrisProfileVO;
import org.example.forumdemo.entity.vo.game.TetrisRecordVO;
import org.example.forumdemo.entity.vo.game.TetrisReplayVO;
import org.example.forumdemo.entity.vo.game.TetrisSettleResultVO;
import org.example.forumdemo.mapper.GameTetrisRecordMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.TetrisService;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 俄罗斯方块单人模式服务：结算、历史、排行榜
@Service
public class TetrisServiceImpl implements TetrisService {

    @Autowired
    private GameTetrisRecordMapper gameTetrisRecordMapper;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TetrisScoreValidator tetrisScoreValidator;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public TetrisProfileVO getProfile(Long userId) {
        GameUserProfile profile = ensureTetrisProfile(userId);
        User user = userMapper.selectById(userId);
        return TetrisConverter.toProfileVO(profile, user);
    }

    @Override
    public PageResult<TetrisRecordVO> listRecords(Long userId, Integer pageNum, Integer pageSize) {
        ensureTetrisProfile(userId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameTetrisRecord> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<GameTetrisRecord> wrapper = new LambdaQueryWrapper<GameTetrisRecord>()
                .eq(GameTetrisRecord::getUserId, userId)
                .eq(GameTetrisRecord::getGameCode, TetrisConstants.GAME_CODE)
                .eq(GameTetrisRecord::getDeleteState, (byte) 0)
                .eq(GameTetrisRecord::getValidationStatus, TetrisConstants.VALIDATION_VALID)
                .orderByDesc(GameTetrisRecord::getEndedAt)
                .orderByDesc(GameTetrisRecord::getId);
        Page<GameTetrisRecord> result = gameTetrisRecordMapper.selectPage(page, wrapper);
        List<TetrisRecordVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameTetrisRecord record : result.getRecords()) {
            rows.add(TetrisConverter.toRecordVO(record));
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
    public List<TetrisProfileVO> listLeaderboard(Integer pageSize) {
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameUserProfile> page = new Page<>(1, validPageSize);
        LambdaQueryWrapper<GameUserProfile> wrapper = new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, TetrisConstants.GAME_CODE)
                .eq(GameUserProfile::getDeleteState, (byte) 0)
                .orderByDesc(GameUserProfile::getScore)
                .orderByDesc(GameUserProfile::getTotalCount)
                .orderByAsc(GameUserProfile::getId);
        Page<GameUserProfile> result = gameUserProfileMapper.selectPage(page, wrapper);
        List<TetrisProfileVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameUserProfile profile : result.getRecords()) {
            User user = userMapper.selectById(profile.getUserId());
            rows.add(TetrisConverter.toProfileVO(profile, user));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TetrisSettleResultVO settle(Long userId, TetrisSettleRequest request) {
        assertSettleRateLimit(userId);
        tetrisScoreValidator.validate(request);
        ensureTetrisProfile(userId);
        GameUserProfile before = selectTetrisProfile(userId);
        int previousBest = before == null || before.getScore() == null ? 0 : before.getScore();
        int forumPoints = tetrisScoreValidator.resolveForumPoints(request.getScore());
        Date endedAt = new Date();
        Date startedAt = request.getStartedAtMs() != null
                ? new Date(request.getStartedAtMs())
                : new Date(endedAt.getTime() - request.getDurationMs());

        GameTetrisRecord record = new GameTetrisRecord();
        record.setUserId(userId);
        record.setGameCode(TetrisConstants.GAME_CODE);
        record.setScore(request.getScore());
        record.setLevel(request.getLevel());
        record.setLinesCleared(request.getLinesCleared());
        record.setDurationMs(request.getDurationMs());
        record.setSeed(request.getSeed());
        record.setReplayPayload(request.getReplayPayload());
        record.setForumPointsAwarded(forumPoints);
        record.setValidationStatus(TetrisConstants.VALIDATION_VALID);
        record.setStartedAt(startedAt);
        record.setEndedAt(endedAt);
        record.setDeleteState((byte) 0);
        gameTetrisRecordMapper.insert(record);

        gameUserProfileMapper.applyTetrisFinish(userId, TetrisConstants.GAME_CODE, request.getScore());
        if (forumPoints > 0) {
            pointsService.addPoints(
                    userId,
                    forumPoints,
                    Constant.POINTS_SOURCE_TETRIS,
                    record.getId(),
                    "俄罗斯方块单局奖励",
                    "game:tetris:solo:" + record.getId()
            );
        }

        GameUserProfile after = selectTetrisProfile(userId);
        int bestScore = after == null ? request.getScore() : after.getScore();
        boolean newBest = request.getScore() > previousBest;
        return new TetrisSettleResultVO(
                record.getId(),
                request.getScore(),
                bestScore,
                forumPoints,
                newBest
        );
    }

    @Override
    public TetrisReplayVO getReplay(Long userId, Long recordId) {
        if (recordId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GameTetrisRecord record = gameTetrisRecordMapper.selectById(recordId);
        if (record == null
                || record.getDeleteState() == null
                || record.getDeleteState() != 0
                || !userId.equals(record.getUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        return new TetrisReplayVO(
                TetrisConverter.toRecordVO(record),
                record.getSeed(),
                record.getReplayPayload()
        );
    }

    private GameUserProfile ensureTetrisProfile(Long userId) {
        GameUserProfile existing = selectTetrisProfile(userId);
        if (existing != null) {
            return existing;
        }
        GameUserProfile profile = new GameUserProfile();
        profile.setUserId(userId);
        profile.setGameCode(TetrisConstants.GAME_CODE);
        profile.setScore(0);
        profile.setTotalCount(0);
        profile.setWinCount(0);
        profile.setLoseCount(0);
        profile.setDrawCount(0);
        profile.setCurrentStatus(GameConstants.PROFILE_IDLE);
        profile.setDeleteState((byte) 0);
        try {
            gameUserProfileMapper.insert(profile);
        } catch (Exception e) {
            GameUserProfile concurrent = selectTetrisProfile(userId);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
        return profile;
    }

    private GameUserProfile selectTetrisProfile(Long userId) {
        List<GameUserProfile> rows = gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getUserId, userId)
                .eq(GameUserProfile::getGameCode, TetrisConstants.GAME_CODE)
                .eq(GameUserProfile::getDeleteState, (byte) 0));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void assertSettleRateLimit(Long userId) {
        String key = GameRedisKeys.tetrisSettleRate(userId);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, TetrisConstants.SETTLE_RATE_LIMIT_TTL_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > TetrisConstants.SETTLE_RATE_LIMIT_PER_HOUR) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_TETRIS_SETTLE_RATE_LIMIT));
        }
    }
}
