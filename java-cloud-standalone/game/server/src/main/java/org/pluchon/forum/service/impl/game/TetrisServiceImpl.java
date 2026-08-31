package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.common.constant.GameRedisKeys;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.converter.TetrisConverter;
import org.pluchon.forum.entity.db.GameTetrisRecord;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.dto.game.TetrisSettleRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.TetrisProfileVO;
import org.pluchon.forum.entity.vo.game.TetrisRecordVO;
import org.pluchon.forum.entity.vo.game.TetrisReplayVO;
import org.pluchon.forum.entity.vo.game.TetrisSettleResultVO;
import org.pluchon.forum.mapper.GameTetrisRecordMapper;
import org.pluchon.forum.mapper.GameUserProfileMapper;
import org.pluchon.forum.service.security.GameUserLookupService;
import org.pluchon.forum.service.interfaces.game.TetrisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// 俄罗斯方块单人模式服务：结算、历史、排行榜
@Slf4j
@Service
public class TetrisServiceImpl implements TetrisService {

    @Autowired
    private GameTetrisRecordMapper gameTetrisRecordMapper;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameUserLookupService gameUserLookupService;

    @Autowired
    private TetrisScoreValidator tetrisScoreValidator;

    @Autowired
    private TetrisReplayVerifier tetrisReplayVerifier;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public TetrisProfileVO getProfile(Long userId) {
        GameUserProfile profile = ensureTetrisProfile(userId);
        UserInternalVO user = gameUserLookupService.getById(userId);
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
                .eq(GameTetrisRecord::getDeleteState, GameConstants.NOT_DELETED)
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
    public PageResult<TetrisProfileVO> listLeaderboard(Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GameUserProfile> page = new Page<>(validPageNum, validPageSize);
        LambdaQueryWrapper<GameUserProfile> wrapper = new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, TetrisConstants.GAME_CODE)
                .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED)
                .orderByDesc(GameUserProfile::getScore)
                .orderByDesc(GameUserProfile::getTotalCount)
                .orderByAsc(GameUserProfile::getId);
        Page<GameUserProfile> result = gameUserProfileMapper.selectPage(page, wrapper);
        List<Long> userIds = result.getRecords().stream().map(GameUserProfile::getUserId).toList();
        Map<Long, UserInternalVO> userMap = userIds.isEmpty() ? Map.of() : gameUserLookupService.loadActiveUsers(userIds);
        List<TetrisProfileVO> rows = new ArrayList<>(result.getRecords().size());
        for (GameUserProfile profile : result.getRecords()) {
            rows.add(TetrisConverter.toProfileVO(profile, userMap.get(profile.getUserId())));
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
    @Transactional(rollbackFor = Exception.class)
    public TetrisSettleResultVO settle(Long userId, TetrisSettleRequest request) {
        assertSettleRateLimit(userId);
        tetrisScoreValidator.validate(request);
        ensureTetrisProfile(userId);
        GameUserProfile before = selectTetrisProfile(userId);
        int previousBest = before == null || before.getScore() == null ? 0 : before.getScore();
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
        record.setForumPointsAwarded(0);
        // 灰度期：只记录重放结论，不据此拒绝。服务端引擎万一与前端有细微差异，
        // 宁可漏过作弊也不能把真实成绩判死；等 MISMATCH 率确认为 0 再改成拒绝
        TetrisReplayVerifier.Result replay = tetrisReplayVerifier.verify(request.getReplayPayload());
        if (!replay.verified()) {
            record.setValidationStatus(TetrisConstants.VALIDATION_SKIPPED);
        } else {
            record.setReplayScore(replay.score());
            record.setValidationStatus(replay.score() == request.getScore()
                    ? TetrisConstants.VALIDATION_REPLAY_OK
                    : TetrisConstants.VALIDATION_MISMATCH);
            if (replay.score() != request.getScore()) {
                log.warn("俄罗斯方块重放分数不一致 userId={} 自报={} 重放={} 行数自报={} 重放={}",
                        userId, request.getScore(), replay.score(),
                        request.getLinesCleared(), replay.lines());
            }
        }
        record.setStartedAt(startedAt);
        record.setEndedAt(endedAt);
        record.setDeleteState(GameConstants.NOT_DELETED);
        gameTetrisRecordMapper.insert(record);

        gameUserProfileMapper.applyTetrisFinish(userId, TetrisConstants.GAME_CODE, request.getScore());
        GameUserProfile after = selectTetrisProfile(userId);
        int bestScore = after == null ? request.getScore() : after.getScore();
        boolean newBest = request.getScore() > previousBest;
        return new TetrisSettleResultVO(
                record.getId(),
                request.getScore(),
                bestScore,
                0,
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
        profile.setDeleteState(GameConstants.NOT_DELETED);
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
                .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED));
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
