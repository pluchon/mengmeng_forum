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

import java.util.List;

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
        // 存活期只要远长于一局即可。房号是六位数字且只跟当前在用的房间比对，
        // 留七天意味着几天后复用到同一个房号时，整局会被当成「已结算过」直接跳过，
        // 双方的分数与状态都不会更新。
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(6));
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

    // 新手保护区上界：低于此分正常失败只扣 3
    private static final int ROOKIE_FLOOR = 1300;
    // 新手加速的消失点，约等于白银段结束
    private static final int ROOKIE_BOOST_CEILING = 1600;

    private int computeWinnerDelta(GameRankSettlementCommand command, GameUserProfile winner, GameUserProfile loser) {
        int base = weighted(baseDelta(winner.getScore(), loser.getScore()), command.getGameCode());
        int delta = base + streakBonus(command.getGameCode(), winner.getUserId())
                + rookieBoost(winner.getScore());
        return Math.min(40, Math.max(1, delta));
    }

    private int computeLoserDelta(GameRankSettlementCommand command, GameUserProfile loser, GameUserProfile winner) {
        int loserScore = GameRankRules.normalizeScore(loser.getScore());
        if (isEscapeReason(command.getEndReason())) {
            return loserScore < ROOKIE_FLOOR ? -8 : -15;
        }
        if (loserScore < ROOKIE_FLOOR) {
            // 新手区少扣而不是不扣：完全不扣会让这一段变成只涨不跌的参与奖，
            // 玩家无论胜率如何都必然升段
            return -3;
        }
        int base = weighted(baseDelta(winner.getScore(), loser.getScore()), command.getGameCode());
        // 连败减免与连胜加成对称，两边的注入量在人群上大致抵消
        int penalty = base - streakRelief(command.getGameCode(), loser.getUserId());
        return -Math.min(20, Math.max(1, penalty));
    }

    private int weighted(int value, String gameCode) {
        return Math.max(1, (int) Math.round(value * GameRankRules.gameWeight(gameCode)));
    }

    /**
     * 胜负共用同一张基础表，赢家加多少输家就扣多少。
     *
     * <p>原来赢用一张表（18~30）、输用另一张（6~12），同段位对局赢 22 输 10，
     * 每局系统净增 12 分——分数只涨不跌，活跃玩家最终都会漂到大师，段位失去区分度。
     *
     * <p>而且原来的输分表方向是反的：输给强者扣 12、输给弱者只扣 6。Elo 的直觉相反，
     * 输给比自己弱的人才是更大的意外，应该扣得更多。这里用同一个「输家段位 - 赢家段位」
     * 轴表达，方向和零和一次性都对了。
     */
    private int baseDelta(Integer winnerScore, Integer loserScore) {
        int diff = GameRankRules.segment(loserScore) - GameRankRules.segment(winnerScore);
        if (diff >= 2) {
            // 赢家越级挑战成功；反过来看就是输家输给了远低于自己的人
            return 20;
        }
        if (diff == 1) {
            return 18;
        }
        if (diff == -1) {
            return 12;
        }
        if (diff <= -2) {
            // 强者赢弱者，几乎无信息量
            return 11;
        }
        return 15;
    }

    /**
     * 新手加速：低分段赢一局多给几分，随分数衰减，出了 {@value #ROOKIE_BOOST_CEILING} 就归零。
     *
     * <p>这是全局唯一一处有意的分数注入。玩家很快会离开这个区间，所以注入总量有界，
     * 不会像原来那样让整个天梯持续通胀。
     */
    private int rookieBoost(Integer winnerScore) {
        int score = GameRankRules.normalizeScore(winnerScore);
        if (score < ROOKIE_FLOOR) {
            return 5;
        }
        if (score < ROOKIE_BOOST_CEILING) {
            return 3;
        }
        return 0;
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

    // 连败减免，档位与 streakBonus 完全对称
    private int streakRelief(String gameCode, Long userId) {
        int currentStreak = consecutiveLosses(gameCode, userId) + 1;
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
        return streakOf(gameCode, userId, true);
    }

    private int consecutiveLosses(String gameCode, Long userId) {
        return streakOf(gameCode, userId, false);
    }

    /**
     * 从最近一局往回数连续的胜（或负）场次。
     *
     * <p>三种棋的记录表结构不同，差异只在「跳过哪些对局」和「胜者字段」，
     * 这里统一成同一段遍历，避免连胜连败各写三份。
     */
    private int streakOf(String gameCode, Long userId, boolean wantWin) {
        if (GameConstants.GOBANG.equals(gameCode)) {
            Page<GameGobangMatchRecord> result = gameGobangMatchRecordMapper.selectPage(new Page<>(1, 20),
                    new LambdaQueryWrapper<GameGobangMatchRecord>()
                            .eq(GameGobangMatchRecord::getDeleteState, GameConstants.NOT_DELETED)
                            .and(w -> w.eq(GameGobangMatchRecord::getBlackUserId, userId)
                                    .or()
                                    .eq(GameGobangMatchRecord::getWhiteUserId, userId))
                            .orderByDesc(GameGobangMatchRecord::getEndedAt)
                            .orderByDesc(GameGobangMatchRecord::getId));
            // 人机对局不计入连胜连败
            return countStreak(result.getRecords(), userId, wantWin,
                    this::isGobangAiMatch, GameGobangMatchRecord::getWinnerUserId);
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
            return countStreak(result.getRecords(), userId, wantWin,
                    record -> false, GameJinziMatchRecord::getWinnerUserId);
        }
        Page<GameTetrisPkMatchRecord> result = gameTetrisPkMatchRecordMapper.selectPage(new Page<>(1, 20),
                new LambdaQueryWrapper<GameTetrisPkMatchRecord>()
                        .eq(GameTetrisPkMatchRecord::getDeleteState, GameConstants.NOT_DELETED)
                        .and(w -> w.eq(GameTetrisPkMatchRecord::getPlayer1UserId, userId)
                                .or()
                                .eq(GameTetrisPkMatchRecord::getPlayer2UserId, userId))
                        .orderByDesc(GameTetrisPkMatchRecord::getEndedAt)
                        .orderByDesc(GameTetrisPkMatchRecord::getId));
        // 双方最高分未达 300 的对局不计入连胜连败
        return countStreak(result.getRecords(), userId, wantWin,
                record -> tetrisPkMaxScore(record) < 300, GameTetrisPkMatchRecord::getWinnerUserId);
    }

    private <T> int countStreak(List<T> records, Long userId, boolean wantWin,
                                java.util.function.Predicate<T> skip,
                                java.util.function.Function<T, Long> winnerOf) {
        int count = 0;
        for (T record : records) {
            if (skip.test(record)) {
                continue;
            }
            Long winner = winnerOf.apply(record);
            // 平局既不算连胜也不算连败，遇到就中断
            if (winner == null) {
                break;
            }
            if (userId.equals(winner) != wantWin) {
                break;
            }
            count++;
        }
        return count;
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
