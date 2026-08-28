package org.pluchon.forum.service.impl.game.ai;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.service.impl.game.GobangRoom;

import java.util.concurrent.ThreadLocalRandom;

// AI 落子调度：决定何时调用大模型、思考最短展示时长
public final class GameAiPlanner {

    private static final String AI_MODEL_PRO = "qwen3.7-max";

    // 候选分差低于此值视为局面胶着，适合交给 LLM 在候选中择优
    private static final int CLOSE_SCORE_SPREAD = 2_000;

    private GameAiPlanner() {
    }

    public static boolean shouldConsultLlm(GobangRoom room, int candidateScoreSpread) {
        if (room == null || !room.isAiRoom()) {
            return false;
        }
        int moveNo = countStones(room.getBoard());
        if (!"midgame".equals(phaseOf(moveNo))) {
            return false;
        }
        if (candidateScoreSpread > CLOSE_SCORE_SPREAD) {
            return false;
        }
        int aiTurn = room.getAiMoveCount();
        boolean pro = AI_MODEL_PRO.equals(room.getAiModelCode());
        if (pro) {
            if (moveNo < 8) {
                return false;
            }
            // 约每 2～3 手咨询一次（取每 2 手）
            return aiTurn % 2 == 0;
        }
        if (moveNo < 12) {
            return false;
        }
        // flash：低频
        return aiTurn % 4 == 0;
    }

    public static long minThinkMs(boolean consultLlm) {
        if (!consultLlm) {
            return 320L;
        }
        return 650L + ThreadLocalRandom.current().nextLong(251L);
    }

    public static String phaseOf(int moveNo) {
        if (moveNo < 10) {
            return "opening";
        }
        if (moveNo < 40) {
            return "midgame";
        }
        return "endgame";
    }

    public static String formatModelLabel(String modelCode, boolean usedLlm, boolean fallback) {
        String safe = AI_MODEL_PRO.equals(modelCode) ? AI_MODEL_PRO : "qwen3.7-flash";
        if (usedLlm && !fallback) {
            return safe + " · Qwen";
        }
        return safe + " · 智能引擎";
    }

    public static int countStones(int[][] board) {
        if (board == null) {
            return 0;
        }
        int count = 0;
        for (int[] row : board) {
            if (row == null) {
                continue;
            }
            for (int cell : row) {
                if (cell != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
