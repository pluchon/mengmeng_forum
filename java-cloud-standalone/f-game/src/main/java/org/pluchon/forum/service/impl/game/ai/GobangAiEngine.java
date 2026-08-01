package org.pluchon.forum.service.impl.game.ai;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.service.impl.game.GobangRuleEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 五子棋本地 AI：战术手 + 浅层 minimax + 启发式评分
@Component
public class GobangAiEngine {

    private static final int AI_CHESS = 2;

    private static final int PLAYER_CHESS = 1;

    private static final int SEARCH_DEPTH = 2;

    private static final int MAX_CANDIDATES = 10;

    private final GobangRuleEngine ruleEngine;

    public GobangAiEngine(GobangRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public boolean hasTacticalMove(int[][] board) {
        return findTacticalMove(board, AI_CHESS) != null || findTacticalMove(board, PLAYER_CHESS) != null;
    }

    public int[] chooseMove(int[][] board) {
        int[] tactical = findTacticalMove(board, AI_CHESS);
        if (tactical != null) {
            return tactical;
        }
        int[] block = findTacticalMove(board, PLAYER_CHESS);
        if (block != null) {
            return block;
        }
        int[] searched = searchBestMove(board);
        return searched != null ? searched : firstEmpty(board);
    }

    private int[] searchBestMove(int[][] board) {
        List<int[]> candidates = rankedCandidates(board);
        if (candidates.isEmpty()) {
            return null;
        }
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[] move : candidates) {
            int row = move[0];
            int col = move[1];
            board[row][col] = AI_CHESS;
            int score = minimax(board, SEARCH_DEPTH - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
            board[row][col] = 0;
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int minimax(int[][] board, int depth, boolean aiTurn, int alpha, int beta) {
        if (depth <= 0 || isTerminal(board)) {
            return evaluateBoard(board);
        }
        List<int[]> moves = rankedCandidates(board);
        if (moves.isEmpty()) {
            return evaluateBoard(board);
        }
        if (aiTurn) {
            int value = Integer.MIN_VALUE;
            for (int[] move : moves) {
                int row = move[0];
                int col = move[1];
                board[row][col] = AI_CHESS;
                value = Math.max(value, minimax(board, depth - 1, false, alpha, beta));
                board[row][col] = 0;
                alpha = Math.max(alpha, value);
                if (beta <= alpha) {
                    break;
                }
            }
            return value;
        }
        int value = Integer.MAX_VALUE;
        for (int[] move : moves) {
            int row = move[0];
            int col = move[1];
            board[row][col] = PLAYER_CHESS;
            value = Math.min(value, minimax(board, depth - 1, true, alpha, beta));
            board[row][col] = 0;
            beta = Math.min(beta, value);
            if (beta <= alpha) {
                break;
            }
        }
        return value;
    }

    private boolean isTerminal(int[][] board) {
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                int cell = board[row][col];
                if (cell == 0) {
                    continue;
                }
                if (ruleEngine.hasFive(board, cell, row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int evaluateBoard(int[][] board) {
        int score = 0;
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] == 0 || !hasNeighbor(board, row, col)) {
                    continue;
                }
                int chess = board[row][col];
                int point = scorePoint(board, row, col, chess);
                score += chess == AI_CHESS ? point : -point;
            }
        }
        return score;
    }

    private List<int[]> rankedCandidates(int[][] board) {
        List<int[]> rows = new ArrayList<>();
        int center = GameConstants.BOARD_SIZE / 2;
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] != 0 || !hasNeighbor(board, row, col)) {
                    continue;
                }
                int heuristic = scorePoint(board, row, col, AI_CHESS) * 2 + scorePoint(board, row, col, PLAYER_CHESS);
                heuristic += 20 - Math.abs(row - center) - Math.abs(col - center);
                rows.add(new int[] { row, col, heuristic });
            }
        }
        if (rows.isEmpty() && board[center][center] == 0) {
            return List.of(new int[] { center, center });
        }
        rows.sort(Comparator.comparingInt((int[] item) -> item[2]).reversed());
        List<int[]> moves = new ArrayList<>(Math.min(MAX_CANDIDATES, rows.size()));
        for (int i = 0; i < Math.min(MAX_CANDIDATES, rows.size()); i++) {
            int[] item = rows.get(i);
            moves.add(new int[] { item[0], item[1] });
        }
        return moves;
    }

    private int[] findTacticalMove(int[][] board, int chess) {
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] != 0) {
                    continue;
                }
                board[row][col] = chess;
                boolean five = ruleEngine.hasFive(board, chess, row, col);
                board[row][col] = 0;
                if (five) {
                    return new int[] { row, col };
                }
            }
        }
        return null;
    }

    private int scorePoint(int[][] board, int row, int col, int chess) {
        int score = 0;
        int[][] directions = new int[][] {
                { 1, 0 },
                { 0, 1 },
                { 1, 1 },
                { 1, -1 }
        };
        for (int[] direction : directions) {
            int forward = countDirection(board, row, col, chess, direction[0], direction[1]);
            int backward = countDirection(board, row, col, chess, -direction[0], -direction[1]);
            int length = forward + backward + 1;
            int openEnds = openEnd(board, row, col, chess, direction[0], direction[1], forward)
                    + openEnd(board, row, col, chess, -direction[0], -direction[1], backward);
            if (length >= 5) {
                score += 100_000;
            } else if (length == 4 && openEnds == 2) {
                score += 12_000;
            } else if (length == 4) {
                score += 5_000;
            } else if (length == 3 && openEnds == 2) {
                score += 1_200;
            } else if (length == 3) {
                score += 360;
            } else if (length == 2 && openEnds == 2) {
                score += 120;
            } else if (length == 2) {
                score += 40;
            } else if (openEnds > 0) {
                score += 8;
            }
        }
        return score;
    }

    private int countDirection(int[][] board, int row, int col, int chess, int rowStep, int colStep) {
        int count = 0;
        int nextRow = row + rowStep;
        int nextCol = col + colStep;
        while (inBoard(nextRow, nextCol) && board[nextRow][nextCol] == chess) {
            count++;
            nextRow += rowStep;
            nextCol += colStep;
        }
        return count;
    }

    private int openEnd(int[][] board, int row, int col, int chess, int rowStep, int colStep, int count) {
        int nextRow = row + rowStep * (count + 1);
        int nextCol = col + colStep * (count + 1);
        return inBoard(nextRow, nextCol) && board[nextRow][nextCol] == 0 ? 1 : 0;
    }

    private boolean hasNeighbor(int[][] board, int row, int col) {
        for (int rowStep = -2; rowStep <= 2; rowStep++) {
            for (int colStep = -2; colStep <= 2; colStep++) {
                if (rowStep == 0 && colStep == 0) {
                    continue;
                }
                int nextRow = row + rowStep;
                int nextCol = col + colStep;
                if (inBoard(nextRow, nextCol) && board[nextRow][nextCol] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] firstEmpty(int[][] board) {
        int center = GameConstants.BOARD_SIZE / 2;
        if (board[center][center] == 0) {
            return new int[] { center, center };
        }
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] == 0) {
                    return new int[] { row, col };
                }
            }
        }
        return null;
    }

    private boolean inBoard(int row, int col) {
        return row >= 0 && row < GameConstants.BOARD_SIZE && col >= 0 && col < GameConstants.BOARD_SIZE;
    }
}
