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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public GameRankInfoVO buildRankInfo(String gameCode, Integer score) {
        return GameRankRules.buildRankInfo(gameCode, score);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameRankSettlementResult settleRank(GameRankSettlementCommand command) {
        validateCommand(command);
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

    private GameRankSettlementResult settleGobangAiRank(GameRankSettlementCommand command) {
        if (!GameConstants.GOBANG.equals(command.getGameCode())) {
            resetProfileStatus(command.getPlayerAUserId(), command.getGameCode());
            resetProfileStatus(command.getPlayerBUserId(), command.getGameCode());
            return new GameRankSettlementResult(false, false, null, null);
        }
        Long humanUserId = GameConstants.AI_USER_ID.equals(command.getPlayerAUserId())
                ? command.getPlayerBUserId()
                : command.getPlayerAUserId();
        GameUserProfile human = gameUserProfileService.getOrCreateProfile(humanUserId, GameConstants.GOBANG);
        if (command.getWinnerUserId() == null || command.getLoserUserId() == null) {
            GameRankPlayerChange change = updateDrawProfile(human, GameConstants.GOBANG);
            return new GameRankSettlementResult(true, true, change, null);
        }
        boolean humanWon = humanUserId.equals(command.getWinnerUserId());
        boolean effective = Boolean.TRUE.equals(command.getEffectiveForRank());
        if (humanWon) {
            int normalDelta = baseWinDelta(human.getScore(), human.getScore())
                    + streakBonus(GameConstants.GOBANG, humanUserId);
            int delta = effective ? GameRankRules.gobangAiWeighted(normalDelta) : 0;
            GameRankPlayerChange change = updateWinProfile(human, GameConstants.GOBANG, delta);
            return new GameRankSettlementResult(effective, false, change, null);
        }
        int normalPenalty = isEscapeReason(command.getEndReason())
                ? 12
                : baseLosePenalty(human.getScore(), human.getScore());
        int delta = effective ? -GameRankRules.gobangAiWeighted(normalPenalty) : 0;
        GameRankPlayerChange change = updateLoseProfile(human, GameConstants.GOBANG, delta);
        return new GameRankSettlementResult(effective, false, null, change);
    }

    private int computeWinnerDelta(GameRankSettlementCommand command, GameUserProfile winner, GameUserProfile loser) {
        int delta = weighted(baseWinDelta(winner.getScore(), loser.getScore()), command.getGameCode());
        delta += streakBonus(command.getGameCode(), winner.getUserId());
        return Math.min(20, Math.max(1, delta));
    }

    private int computeLoserDelta(GameRankSettlementCommand command, GameUserProfile loser, GameUserProfile winner) {
        if (isEscapeReason(command.getEndReason())) {
            return -12;
        }
        int penalty = weighted(baseLosePenalty(loser.getScore(), winner.getScore()), command.getGameCode());
        return -Math.min(15, Math.max(1, penalty));
    }

    private int weighted(int value, String gameCode) {
        return Math.max(1, (int) Math.round(value * GameRankRules.gameWeight(gameCode)));
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
        if (update.getCurrentRoomId() == null) {
            wrapper.set(GameUserProfile::getCurrentRoomId, null);
        }
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

    private int value(Integer n) {
        return n == null ? 0 : n;
    }
}
