package org.pluchon.forum.service.impl.game;

import org.pluchon.forum.entity.vo.game.GameRankInfoVO;

// 游戏排位规则工具，集中维护段位展示与分数边界
public class GameRankRules {

    private static final int MIN_SCORE = 1000;

    private static final int MASTER_SCORE = 2500;

    private static final int TIER_SCORE = 100;

    private static final double GOBANG_AI_RANK_WEIGHT = 0.8D;

    private static final String[] MAJOR_NAMES = {"青铜", "白银", "黄金", "铂金", "钻石"};

    private static final String[] TIER_NAMES = {"III", "II", "I"};

    private GameRankRules() {
    }

    public static int minScore() {
        return MIN_SCORE;
    }

    public static int segment(Integer score) {
        int safeScore = normalizeScore(score);
        return Math.max(0, (safeScore - MIN_SCORE) / TIER_SCORE);
    }

    public static int normalizeScore(Integer score) {
        return Math.max(MIN_SCORE, score == null ? MIN_SCORE : score);
    }

    public static GameRankInfoVO buildRankInfo(String gameCode, Integer score) {
        int safeScore = normalizeScore(score);
        if (safeScore >= MASTER_SCORE) {
            return new GameRankInfoVO(
                    rankName("大师", null),
                    "大师",
                    null,
                    MASTER_SCORE,
                    null,
                    null,
                    0,
                    100
            );
        }
        int segment = segment(safeScore);
        int majorIndex = Math.min(MAJOR_NAMES.length - 1, segment / 3);
        int tierIndex = segment % 3;
        int min = MIN_SCORE + segment * TIER_SCORE;
        int next = min + TIER_SCORE;
        int progress = Math.max(0, Math.min(99, safeScore - min));
        return new GameRankInfoVO(
                rankName(MAJOR_NAMES[majorIndex], TIER_NAMES[tierIndex]),
                MAJOR_NAMES[majorIndex],
                TIER_NAMES[tierIndex],
                min,
                next - 1,
                next,
                Math.max(0, next - safeScore),
                progress
        );
    }

    public static double gameWeight(String gameCode) {
        if (GameConstants.JINZI.equals(gameCode)) {
            return 0.85D;
        }
        if (GameConstants.TETRIS_PK.equals(gameCode)) {
            return 0.85D;
        }
        return 1.0D;
    }

    public static int gobangAiWeighted(int value) {
        return Math.max(1, (int) Math.round(value * GOBANG_AI_RANK_WEIGHT));
    }

    public static String rankName(String majorName, String tierName) {
        return tierName == null ? majorName : majorName + " " + tierName;
    }
}
