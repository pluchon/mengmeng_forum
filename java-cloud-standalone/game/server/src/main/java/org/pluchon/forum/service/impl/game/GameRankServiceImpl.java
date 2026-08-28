package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.bo.game.GameRankPlayerChange;
import org.pluchon.forum.entity.bo.game.GameRankSettlementCommand;
import org.pluchon.forum.entity.bo.game.GameRankSettlementResult;
import org.pluchon.forum.entity.db.GameGobangMatchRecord;
import org.pluchon.forum.entity.db.GameJinziMatchRecord;
import org.pluchon.forum.entity.db.GameTetrisPkMatchRecord;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.entity.vo.game.GameRankInfoVO;
import org.pluchon.forum.mapper.GameGobangMatchRecordMapper;
import org.pluchon.forum.mapper.GameJinziMatchRecordMapper;
import org.pluchon.forum.mapper.GameTetrisPkMatchRecordMapper;
import org.pluchon.forum.mapper.GameUserProfileMapper;
import org.pluchon.forum.service.interfaces.game.GameRankService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

// 游戏排位结算服务，统一处理真人对局与五子棋人机对局的段位分变化
@Service
public class GameRankServiceImpl implements GameRankService {

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameGobangMatchRecordMapper gameGobangMatchRecordMapper;

    @Autowired
    private GameJinziMatchRecordMapper gameJinziMatchRecordMapper;

    @Autowired
    private GameTetrisPkMatchRecordMapper gameTetrisPkMatchRecordMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public GameRankInfoVO buildRankInfo(String gameCode, Integer score) {
        return GameRankRules.buildRankInfo(gameCode, score);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameRankSettlementResult settleRank(GameRankSettlementCommand command) {
        validateCommand(command);
        if (!claimSettlementOnce(command)) {
            // 同房间已结算过：返回空变化，避免重复加减分
            return new GameRankSettlementResult(null, null);
        }
        if (isAiMatch(command)) {
            return settleGobangAiRank(command);
        }
        GameUserProfile playerA = gameUserProfileService.getOrCreateProfile(
                command.getPlayerAUserId(),
                command.getGameCode()
        );
        GameUserProfile playerB = gameUserProfileService.getOrCreateProfile(
                command.getPlayerBUserId(),
                command.getGameCode()
        );
        if (command.getWinnerUserId() == null || command.getLoserUserId() == null) {
            GameRankPlayerChange changeA = updateDrawProfile(playerA, command.getGameCode());
            GameRankPlayerChange changeB = updateDrawProfile(playerB, command.getGameCode());
            return new GameRankSettlementResult(changeA, changeB);
        }
        GameUserProfile winner = command.getWinnerUserId().equals(playerA.getUserId()) ? playerA : playerB;
        GameUserProfile loser = command.getLoserUserId().equals(playerA.getUserId()) ? playerA : playerB;
        boolean effective = Boolean.TRUE.equals(command.getEffectiveForRank());
        int winnerDelta = effective ? computeWinnerDelta(command, winner, loser) : 0;
        int loserDelta = effective ? computeLoserDelta(command, loser, winner) : 0;
        GameRankPlayerChange winnerChange = updateWinProfile(winner, command.getGameCode(), winnerDelta);
        GameRankPlayerChange loserChange = updateLoseProfile(loser, command.getGameCode(), loserDelta);
        return new GameRankSettlementResult(winnerChange, loserChange);
    }

    private void validateCommand(GameRankSettlementCommand command) {
        if (command == null
                || command.getGameCode() == null
                || command.getGameCode().isBlank()
                || command.getPlayerAUserId() == null
                || command.getPlayerBUserId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    // 同房间排位只结算一次；无 roomId 时跳过（兼容旧调用）
    private boolean claimSettlementOnce(GameRankSettlementCommand command) {
        if (!StringUtils.hasText(command.getRoomId())) {
            return true;
        }
        String key = "forum:game:rank-settle:" + command.getGameCode().trim() + ":" + command.getRoomId().trim();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
        return Boolean.TRUE.equals(first);
    }

    private boolean isAiMatch(GameRankSettlementCommand command) {
        return GameConstants.AI_USER_ID.equals(command.getPlayerAUserId())
                || GameConstants.AI_USER_ID.equals(command.getPlayerBUserId());
    }

    private GameRankSettlementResult settleGobangAiRank(GameRankSettlementCommand command) {
        if (!GameConstants.GOBANG.equals(command.getGameCode())) {
            resetProfileStatus(command.getPlayerAUserId(), command.getGameCode());
            resetProfileStatus(command.getPlayerBUserId(), command.getGameCode());
            return new GameRankSettlementResult(null, null);
        }
        Long humanUserId = GameConstants.AI_USER_ID.equals(command.getPlayerAUserId())
                ? command.getPlayerBUserId()
                : command.getPlayerAUserId();
        GameUserProfile human = gameUserProfileService.getOrCreateProfile(humanUserId, GameConstants.GOBANG);
        if (command.getWinnerUserId() == null || command.getLoserUserId() == null) {
            GameRankPlayerChange change = updateDrawProfile(human, GameConstants.GOBANG);
            return new GameRankSettlementResult(change, null);
        }
        boolean humanWon = humanUserId.equals(command.getWinnerUserId());
        boolean effective = Boolean.TRUE.equals(command.getEffectiveForRank());
        if (humanWon) {
            // 人机胜：固定 +12，不计连胜加成、不按 baseWin×0.8
            int delta = effective ? 12 : 0;
            GameRankPlayerChange change = updateWinProfile(human, GameConstants.GOBANG, delta);
            return new GameRankSettlementResult(change, null);
        }
        // 人机负：正常失败 0；逃跑/断线/超时 -10
        int delta = 0;
        if (effective && isEscapeReason(command.getEndReason())) {
            delta = -10;
        }
        GameRankPlayerChange change = updateLoseProfile(human, GameConstants.GOBANG, delta);
        return new GameRankSettlementResult(null, change);
    }

    private int computeWinnerDelta(GameRankSettlementCommand command, GameUserProfile winner, GameUserProfile loser) {
        int delta = weighted(baseWinDelta(winner.getScore(), loser.getScore()), command.getGameCode());
        delta += streakBonus(command.getGameCode(), winner.getUserId());
        return Math.min(40, Math.max(1, delta));
    }

    private int computeLoserDelta(GameRankSettlementCommand command, GameUserProfile loser, GameUserProfile winner) {
        int loserScore = GameRankRules.normalizeScore(loser.getScore());
        if (isEscapeReason(command.getEndReason())) {
            return loserScore < 1300 ? -5 : -15;
        }
        if (loserScore < 1300) {
            // 青铜段位保护：正常失败不扣分
            return 0;
        }
        int penalty = weighted(baseLosePenalty(loserScore, winner.getScore()), command.getGameCode());
        return -Math.min(20, Math.max(1, penalty));
    }

    private int weighted(int value, String gameCode) {
        return Math.max(1, (int) Math.round(value * GameRankRules.gameWeight(gameCode)));
    }

    private int baseWinDelta(Integer winnerScore, Integer loserScore) {
        int diff = GameRankRules.segment(loserScore) - GameRankRules.segment(winnerScore);
        if (diff >= 2) {
            return 30;
        }
        if (diff == 1) {
            return 28;
        }
        if (diff == -1) {
            return 22;
        }
        if (diff <= -2) {
            return 18;
        }
        return 22;
    }

    private int baseLosePenalty(Integer loserScore, Integer winnerScore) {
        int diff = GameRankRules.segment(winnerScore) - GameRankRules.segment(loserScore);
        if (diff >= 2) {
            return 12;
        }
        if (diff == 1) {
            return 11;
        }
        if (diff == -1) {
            return 8;
        }
        if (diff <= -2) {
            return 6;
        }
        return 10;
    }

    private int streakBonus(String gameCode, Long userId) {
        int previousWins = consecutiveWins(gameCode, userId);
        int currentStreak = previousWins + 1;
        if (currentStreak >= 5) {
            return 8;
        }
        if (currentStreak == 4) {
            return 6;
        }
        if (currentStreak == 3) {
            return 4;
        }
        if (currentStreak == 2) {
            return 2;
        }
        return 0;
    }

    private boolean isEscapeReason(String endReason) {
        return GameConstants.END_SURRENDER.equals(endReason)
                || GameConstants.END_DISCONNECT.equals(endReason)
                || GameConstants.END_TIMEOUT.equals(endReason);
    }

    private GameRankPlayerChange updateWinProfile(GameUserProfile profile, String gameCode, int delta) {
        int before = GameRankRules.normalizeScore(profile.getScore());
        int after = before + Math.max(0, delta);
        GameUserProfile update = new GameUserProfile();
        update.setScore(after);
        update.setTotalCount(value(profile.getTotalCount()) + 1);
        update.setWinCount(value(profile.getWinCount()) + 1);
        update.setCurrentStatus(GameConstants.PROFILE_IDLE);
        update.setCurrentRoomId(null);
        updateProfile(profile.getId(), gameCode, profile.getUserId(), update);
        return buildChange(profile.getUserId(), Math.max(0, delta));
    }

    private GameRankPlayerChange updateLoseProfile(GameUserProfile profile, String gameCode, int delta) {
        int before = GameRankRules.normalizeScore(profile.getScore());
        int after = Math.max(GameRankRules.minScore(), before + Math.min(0, delta));
        GameUserProfile update = new GameUserProfile();
        update.setScore(after);
        update.setTotalCount(value(profile.getTotalCount()) + 1);
        update.setLoseCount(value(profile.getLoseCount()) + 1);
        update.setCurrentStatus(GameConstants.PROFILE_IDLE);
        update.setCurrentRoomId(null);
        updateProfile(profile.getId(), gameCode, profile.getUserId(), update);
        return buildChange(profile.getUserId(), Math.min(0, delta));
    }

    private GameRankPlayerChange updateDrawProfile(GameUserProfile profile, String gameCode) {
        int score = GameRankRules.normalizeScore(profile.getScore());
        GameUserProfile update = new GameUserProfile();
        update.setScore(score);
        update.setTotalCount(value(profile.getTotalCount()) + 1);
        update.setDrawCount(value(profile.getDrawCount()) + 1);
        update.setCurrentStatus(GameConstants.PROFILE_IDLE);
        update.setCurrentRoomId(null);
        updateProfile(profile.getId(), gameCode, profile.getUserId(), update);
        return buildChange(profile.getUserId(), 0);
    }

    private void resetProfileStatus(Long userId, String gameCode) {
        if (userId == null || GameConstants.AI_USER_ID.equals(userId)) {
            return;
        }
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, gameCode);
        GameUserProfile update = new GameUserProfile();
        update.setCurrentStatus(GameConstants.PROFILE_IDLE);
        update.setCurrentRoomId(null);
        updateProfile(profile.getId(), gameCode, userId, update);
    }

    private void updateProfile(Long id, String gameCode, Long userId, GameUserProfile update) {
        LambdaUpdateWrapper<GameUserProfile> wrapper = new LambdaUpdateWrapper<GameUserProfile>()
                .eq(GameUserProfile::getId, id)
                .eq(GameUserProfile::getUserId, userId)
                .eq(GameUserProfile::getGameCode, gameCode)
                .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED);
        if (update.getCurrentRoomId() == null) {
            wrapper.set(GameUserProfile::getCurrentRoomId, null);
        }
        gameUserProfileMapper.update(update, wrapper);
    }

    private GameRankPlayerChange buildChange(Long userId, int delta) {
        return new GameRankPlayerChange(userId, delta);
    }

    private int consecutiveWins(String gameCode, Long userId) {
        if (GameConstants.GOBANG.equals(gameCode)) {
            Page<GameGobangMatchRecord> result = gameGobangMatchRecordMapper.selectPage(new Page<>(1, 20),
                    new LambdaQueryWrapper<GameGobangMatchRecord>()
                            .eq(GameGobangMatchRecord::getDeleteState, GameConstants.NOT_DELETED)
                            .and(w -> w.eq(GameGobangMatchRecord::getBlackUserId, userId)
                                    .or()
                                    .eq(GameGobangMatchRecord::getWhiteUserId, userId))
                            .orderByDesc(GameGobangMatchRecord::getEndedAt)
                            .orderByDesc(GameGobangMatchRecord::getId));
            int wins = 0;
            for (GameGobangMatchRecord record : result.getRecords()) {
                // 人机对局不计入连胜
                if (isGobangAiMatch(record)) {
                    continue;
                }
                if (!userId.equals(record.getWinnerUserId())) {
                    break;
                }
                wins++;
            }
            return wins;
        }
        if (GameConstants.JINZI.equals(gameCode)) {
            Page<GameJinziMatchRecord> result = gameJinziMatchRecordMapper.selectPage(new Page<>(1, 20),
                    new LambdaQueryWrapper<GameJinziMatchRecord>()
                            .eq(GameJinziMatchRecord::getDeleteState, GameConstants.NOT_DELETED)
                            .and(w -> w.eq(GameJinziMatchRecord::getBlackUserId, userId)
                                    .or()
                                    .eq(GameJinziMatchRecord::getWhiteUserId, userId))
                            .orderByDesc(GameJinziMatchRecord::getEndedAt)
                            .orderByDesc(GameJinziMatchRecord::getId));
            int wins = 0;
            for (GameJinziMatchRecord record : result.getRecords()) {
                if (!userId.equals(record.getWinnerUserId())) {
                    break;
                }
                wins++;
            }
            return wins;
        }
        Page<GameTetrisPkMatchRecord> result = gameTetrisPkMatchRecordMapper.selectPage(new Page<>(1, 20),
                new LambdaQueryWrapper<GameTetrisPkMatchRecord>()
                        .eq(GameTetrisPkMatchRecord::getDeleteState, GameConstants.NOT_DELETED)
                        .and(w -> w.eq(GameTetrisPkMatchRecord::getPlayer1UserId, userId)
                                .or()
                                .eq(GameTetrisPkMatchRecord::getPlayer2UserId, userId))
                        .orderByDesc(GameTetrisPkMatchRecord::getEndedAt)
                        .orderByDesc(GameTetrisPkMatchRecord::getId));
        int wins = 0;
        for (GameTetrisPkMatchRecord record : result.getRecords()) {
            // 双方最高分未达 300 的对局不计入连胜
            if (tetrisPkMaxScore(record) < 300) {
                continue;
            }
            if (!userId.equals(record.getWinnerUserId())) {
                break;
            }
            wins++;
        }
        return wins;
    }

    private boolean isGobangAiMatch(GameGobangMatchRecord record) {
        return GameConstants.AI_USER_ID.equals(record.getBlackUserId())
                || GameConstants.AI_USER_ID.equals(record.getWhiteUserId());
    }

    private int tetrisPkMaxScore(GameTetrisPkMatchRecord record) {
        int score1 = value(record.getPlayer1Score());
        int score2 = value(record.getPlayer2Score());
        return Math.max(score1, score2);
    }

    private int value(Integer n) {
        return n == null ? 0 : n;
    }
}
