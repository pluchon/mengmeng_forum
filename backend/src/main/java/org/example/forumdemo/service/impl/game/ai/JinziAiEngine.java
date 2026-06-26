package org.example.forumdemo.service.impl.game.ai;

import org.example.forumdemo.service.impl.game.GameConstants;
import org.example.forumdemo.service.impl.game.JinziRuleEngine;
import org.springframework.stereotype.Component;

// 井字棋 AI：3x3 极小极大搜索，无需调用大模型
@Component
public class JinziAiEngine {

    private static final int WIN_SCORE = 10_000;

    private final JinziRuleEngine ruleEngine;

    public JinziAiEngine(JinziRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public int[] chooseMove(int[][] board, int aiChess) {
        int playerChess = aiChess == 1 ? 2 : 1;
        int[] tactical = findTacticalMove(board, aiChess);
        if (tactical != null) {
            return tactical;
        }
        int[] block = findTacticalMove(board, playerChess);
        if (block != null) {
            return block;
        }
        int[] best = minimaxRoot(board, aiChess, playerChess);
        return best != null ? best : firstEmpty(board);
    }

    private int[] minimaxRoot(int[][] board, int aiChess, int playerChess) {
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        for (int row = 0; row < GameConstants.JINZI_BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.JINZI_BOARD_SIZE; col++) {
                if (board[row][col] != 0) {
                    continue;
                }
                board[row][col] = aiChess;
                int score = minimax(board, false, aiChess, playerChess);
                board[row][col] = 0;
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = new int[] { row, col };
                }
            }
        }
        return bestMove;
    }

    private int minimax(int[][] board, boolean aiTurn, int aiChess, int playerChess) {
        if (ruleEngine.hasLine(board, aiChess)) {
            return WIN_SCORE;
        }
        if (ruleEngine.hasLine(board, playerChess)) {
            return -WIN_SCORE;
        }
        if (ruleEngine.isDraw(board)) {
            return 0;
        }
        int chess = aiTurn ? aiChess : playerChess;
        int best = aiTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int row = 0; row < GameConstants.JINZI_BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.JINZI_BOARD_SIZE; col++) {
                if (board[row][col] != 0) {
                    continue;
                }
                board[row][col] = chess;
                int score = minimax(board, !aiTurn, aiChess, playerChess);
                board[row][col] = 0;
                if (aiTurn) {
                    best = Math.max(best, score);
                } else {
                    best = Math.min(best, score);
                }
            }
        }
        return best;
    }

    private int[] findTacticalMove(int[][] board, int chess) {
        for (int row = 0; row < GameConstants.JINZI_BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.JINZI_BOARD_SIZE; col++) {
                if (board[row][col] != 0) {
                    continue;
                }
                board[row][col] = chess;
                boolean line = ruleEngine.hasLine(board, chess);
                board[row][col] = 0;
                if (line) {
                    return new int[] { row, col };
                }
            }
        }
        return null;
    }

    private int[] firstEmpty(int[][] board) {
        for (int row = 0; row < GameConstants.JINZI_BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.JINZI_BOARD_SIZE; col++) {
                if (board[row][col] == 0) {
                    return new int[] { row, col };
                }
            }
        }
        return null;
    }
}
