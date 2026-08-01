package org.pluchon.forum.service.impl.game.ai;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.service.impl.game.GobangRoom;

// AI 落子调度：决定何时调用大模型、思考最短展示时长
public final class GameAiPlanner {

    private static final String AI_MODEL_PRO = "qwen3.7-max";

    private static final int GOBANG_LLM_MIN_STONES = 6;

    private static final int GOBANG_LLM_MIN_STONES_CASUAL = 8;

    private GameAiPlanner() {
    }

    public static boolean shouldConsultLlm(GobangRoom room) {
        if (room == null || !room.isAiRoom()) {
            return false;
        }
        int stones = countStones(room.getBoard());
        if (stones < GOBANG_LLM_MIN_STONES) {
            return false;
        }
        int aiTurn = room.getAiMoveCount();
        boolean pro = AI_MODEL_PRO.equals(room.getAiModelCode());
        if (pro) {
            return stones >= 4 && aiTurn % 2 == 0;
        }
        return stones >= GOBANG_LLM_MIN_STONES_CASUAL && aiTurn % 3 == 0;
    }

    public static long minThinkMs(boolean consultLlm) {
        return consultLlm ? 650L : 320L;
    }

    public static String formatModelLabel(String modelCode, boolean usedLlm, boolean fallback) {
        String safe = AI_MODEL_PRO.equals(modelCode) ? AI_MODEL_PRO : "qwen3.6-flash";
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

    public static boolean isAiTurn(Long currentTurnUserId) {
        return GameConstants.AI_USER_ID.equals(currentTurnUserId);
    }
}
