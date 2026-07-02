package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.bo.game.GameRankPlayerChange;
import org.example.forumdemo.entity.bo.game.GameRankSettlementCommand;
import org.example.forumdemo.entity.bo.game.GameRankSettlementResult;
import org.example.forumdemo.entity.db.GameGobangMatchRecord;
import org.example.forumdemo.entity.db.GameJinziMatchRecord;
import org.example.forumdemo.entity.db.GameTetrisPkMatchRecord;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.vo.game.GameRankInfoVO;
import org.example.forumdemo.mapper.GameGobangMatchRecordMapper;
import org.example.forumdemo.mapper.GameJinziMatchRecordMapper;
import org.example.forumdemo.mapper.GameTetrisPkMatchRecordMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.service.interfaces.game.GameRankService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

// 游戏排位结算服务，统一处理真人 PK 的段位分变化
@Service
public class GameRankServiceImpl implements GameRankService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

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

    @Override
    public GameRankInfoVO buildRankInfo(String gameCode, Integer score) {
        return GameRankRules.buildRankInfo(gameCode, score);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameRankSettlementResult settleRank(GameRankSettlementCommand command) {
        validateCommand(command);
        if (isAiMatch(command)) {
            resetProfileStatus(command.getPlayerAUserId(), command.getGameCode());
            resetProfileStatus(command.getPlayerBUserId(), command.getGameCode());
            return new GameRankSettlementResult(false, false, null, null);
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
            return new GameRankSettlementResult(true, true, changeA, changeB);
        }
        GameUserProfile winner = command.getWinnerUserId().equals(playerA.getUserId()) ? playerA : playerB;
        GameUserProfile loser = command.getLoserUserId().equals(playerA.getUserId()) ? playerA : playerB;
        boolean effective = Boolean.TRUE.equals(command.getEffectiveForRank());
        int winnerDelta = effective ? computeWinnerDelta(command, winner, loser) : 0;
        int loserDelta = effective ? computeLoserDelta(command, loser, winner) : 0;
        GameRankPlayerChange winnerChange = updateWinProfile(winner, command.getGameCode(), winnerDelta);
        GameRankPlayerChange loserChange = updateLoseProfile(loser, command.getGameCode(), loserDelta);
        return new GameRankSettlementResult(effective, false, winnerChange, loserChange);
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

    private boolean isAiMatch(GameRankSettlementCommand command) {
        return GameConstants.AI_USER_ID.equals(command.getPlayerAUserId())
                || GameConstants.AI_USER_ID.equals(command.getPlayerBUserId());
    }

    private int computeWinnerDelta(GameRankSettlementCommand command, GameUserProfile winner, GameUserProfile loser) {
        int delta = baseWinDelta(winner.getScore(), loser.getScore());
        delta += streakBonus(command.getGameCode(), winner.getUserId());
        int recentCount = countAgainstSince(
                command.getGameCode(),
                winner.getUserId(),
                loser.getUserId(),
                new Date(System.currentTimeMillis() - 30 * 60 * 1000L)
        );
        int todayCount = countAgainstSince(command.getGameCode(), winner.getUserId(), loser.getUserId(), todayStart());
        if (recentCount >= 1 || todayCount >= 3) {
            delta = Math.max(1, delta / 2);
        }
        return Math.min(20, Math.max(1, delta));
    }

    private int computeLoserDelta(GameRankSettlementCommand command, GameUserProfile loser, GameUserProfile winner) {
        if (isEscapeReason(command.getEndReason())) {
            return -12;
        }
        int penalty = baseLosePenalty(loser.getScore(), winner.getScore());
        return -Math.min(15, Math.max(1, penalty));
    }

    private int baseWinDelta(Integer winnerScore, Integer loserScore) {
        int diff = GameRankRules.segment(loserScore) - GameRankRules.segment(winnerScore);
        if (diff >= 2) {
            return 16;
        }
        if (diff == 1) {
            return 14;
        }
        if (diff == -1) {
            return 10;
        }
        if (diff <= -2) {
            return 8;
        }
        return 12;
    }

    private int baseLosePenalty(Integer loserScore, Integer winnerScore) {
        int diff = GameRankRules.segment(winnerScore) - GameRankRules.segment(loserScore);
        if (diff >= 2) {
            return 5;
        }
        if (diff == 1) {
            return 6;
        }
        if (diff == -1) {
            return 9;
        }
        if (diff <= -2) {
            return 11;
        }
        return 8;
    }

    private int streakBonus(String gameCode, Long userId) {
        int previousWins = consecutiveWins(gameCode, userId);
        int currentStreak = previousWins + 1;
        if (currentStreak >= 5) {
            return 4;
        }
        if (currentStreak == 4) {
            return 3;
        }
        if (currentStreak == 3) {
            return 2;
        }
        if (currentStreak == 2) {
            return 1;
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
        return buildChange(profile.getUserId(), gameCode, before, after, Math.max(0, delta));
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
        return buildChange(profile.getUserId(), gameCode, before, after, Math.min(0, delta));
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
        return buildChange(profile.getUserId(), gameCode, score, score, 0);
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
                .eq(GameUserProfile::getDeleteState, (byte) 0);
        gameUserProfileMapper.update(update, wrapper);
    }

    private GameRankPlayerChange buildChange(Long userId, String gameCode, int before, int after, int delta) {
        return new GameRankPlayerChange(
                userId,
                before,
                after,
                delta,
                GameRankRules.buildRankInfo(gameCode, before),
                GameRankRules.buildRankInfo(gameCode, after)
        );
    }

    private int consecutiveWins(String gameCode, Long userId) {
        if (GameConstants.GOBANG.equals(gameCode)) {
            Page<GameGobangMatchRecord> result = gameGobangMatchRecordMapper.selectPage(new Page<>(1, 20),
                    new LambdaQueryWrapper<GameGobangMatchRecord>()
                            .eq(GameGobangMatchRecord::getDeleteState, (byte) 0)
                            .and(w -> w.eq(GameGobangMatchRecord::getBlackUserId, userId)
                                    .or()
                                    .eq(GameGobangMatchRecord::getWhiteUserId, userId))
                            .orderByDesc(GameGobangMatchRecord::getEndedAt)
                            .orderByDesc(GameGobangMatchRecord::getId));
            int wins = 0;
            for (GameGobangMatchRecord record : result.getRecords()) {
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
                            .eq(GameJinziMatchRecord::getDeleteState, (byte) 0)
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
                        .eq(GameTetrisPkMatchRecord::getDeleteState, (byte) 0)
                        .and(w -> w.eq(GameTetrisPkMatchRecord::getPlayer1UserId, userId)
                                .or()
                                .eq(GameTetrisPkMatchRecord::getPlayer2UserId, userId))
                        .orderByDesc(GameTetrisPkMatchRecord::getEndedAt)
                        .orderByDesc(GameTetrisPkMatchRecord::getId));
        int wins = 0;
        for (GameTetrisPkMatchRecord record : result.getRecords()) {
            if (!userId.equals(record.getWinnerUserId())) {
                break;
            }
            wins++;
        }
        return wins;
    }

    private int countAgainstSince(String gameCode, Long userA, Long userB, Date since) {
        if (GameConstants.GOBANG.equals(gameCode)) {
            return Math.toIntExact(gameGobangMatchRecordMapper.selectCount(
                    new LambdaQueryWrapper<GameGobangMatchRecord>()
                            .eq(GameGobangMatchRecord::getDeleteState, (byte) 0)
                            .ge(GameGobangMatchRecord::getEndedAt, since)
                            .and(w -> w.and(x -> x.eq(GameGobangMatchRecord::getBlackUserId, userA)
                                            .eq(GameGobangMatchRecord::getWhiteUserId, userB))
                                    .or(x -> x.eq(GameGobangMatchRecord::getBlackUserId, userB)
                                            .eq(GameGobangMatchRecord::getWhiteUserId, userA)))));
        }
        if (GameConstants.JINZI.equals(gameCode)) {
            return Math.toIntExact(gameJinziMatchRecordMapper.selectCount(
                    new LambdaQueryWrapper<GameJinziMatchRecord>()
                            .eq(GameJinziMatchRecord::getDeleteState, (byte) 0)
                            .ge(GameJinziMatchRecord::getEndedAt, since)
                            .and(w -> w.and(x -> x.eq(GameJinziMatchRecord::getBlackUserId, userA)
                                            .eq(GameJinziMatchRecord::getWhiteUserId, userB))
                                    .or(x -> x.eq(GameJinziMatchRecord::getBlackUserId, userB)
                                            .eq(GameJinziMatchRecord::getWhiteUserId, userA)))));
        }
        return Math.toIntExact(gameTetrisPkMatchRecordMapper.selectCount(
                new LambdaQueryWrapper<GameTetrisPkMatchRecord>()
                        .eq(GameTetrisPkMatchRecord::getDeleteState, (byte) 0)
                        .ge(GameTetrisPkMatchRecord::getEndedAt, since)
                        .and(w -> w.and(x -> x.eq(GameTetrisPkMatchRecord::getPlayer1UserId, userA)
                                        .eq(GameTetrisPkMatchRecord::getPlayer2UserId, userB))
                                .or(x -> x.eq(GameTetrisPkMatchRecord::getPlayer1UserId, userB)
                                        .eq(GameTetrisPkMatchRecord::getPlayer2UserId, userA)))));
    }

    private Date todayStart() {
        return Date.from(LocalDate.now(CHINA_ZONE).atStartOfDay(CHINA_ZONE).toInstant());
    }

    private int value(Integer n) {
        return n == null ? 0 : n;
    }
}
