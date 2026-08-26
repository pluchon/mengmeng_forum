package org.pluchon.forum.service.impl.game.ai;

// 五子棋本地引擎难度：按玩家段位分桶控制搜索深度与候选宽度
public final class GobangAiDifficultyProfile {

    private final int depth;

    private final int maxCandidates;

    private final int scoreBucket;

    private GobangAiDifficultyProfile(int depth, int maxCandidates, int scoreBucket) {
        this.depth = depth;
        this.maxCandidates = maxCandidates;
        this.scoreBucket = scoreBucket;
    }

    public static GobangAiDifficultyProfile ofScore(int score) {
        if (score < 1200) {
            return new GobangAiDifficultyProfile(2, 12, 0);
        }
        if (score < 1600) {
            return new GobangAiDifficultyProfile(3, 15, 1);
        }
        return new GobangAiDifficultyProfile(4, 18, 2);
    }

    public static GobangAiDifficultyProfile of(int depth, int maxCandidates) {
        return new GobangAiDifficultyProfile(Math.max(1, depth), Math.max(1, maxCandidates), 0);
    }

    public int depth() {
        return depth;
    }

    public int maxCandidates() {
        return maxCandidates;
    }

    public int scoreBucket() {
        return scoreBucket;
    }
}
