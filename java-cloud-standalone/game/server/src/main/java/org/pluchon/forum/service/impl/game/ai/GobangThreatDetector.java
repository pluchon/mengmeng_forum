package org.pluchon.forum.service.impl.game.ai;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 五子棋威胁检测：按 P0～P4 优先级识别强制手与战术手
@Component
public class GobangThreatDetector {

    public static final int P0_WIN = 0;

    public static final int P1_BLOCK_FIVE = 1;

    public static final int P2_BLOCK_FOUR = 2;

    public static final int P3_ATTACK = 3;

    public static final int P4_BLOCK_LIVE_THREE = 4;

    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };

    public record ThreatHit(int priority, int row, int col, String reason) {
    }

    public record PatternStats(int fives, int openFours, int rushFours, int liveThrees) {
        boolean isFive() {
            return fives > 0;
        }

        boolean isOpenOrRushFour() {
            return openFours > 0 || rushFours > 0;
        }

        boolean isDoubleLiveThree() {
            return liveThrees >= 2;
        }

        boolean isLiveThree() {
            return liveThrees > 0;
        }
    }

    public ThreatHit findBestThreat(int[][] board, int myChess, int oppChess) {
        ThreatHit p0 = findPlacement(board, myChess, P0_WIN, "win_five");
        if (p0 != null) {
            return p0;
        }
        ThreatHit p1 = findPlacement(board, oppChess, P1_BLOCK_FIVE, "block_five");
        if (p1 != null) {
            return p1;
        }
        ThreatHit p2 = findPlacement(board, oppChess, P2_BLOCK_FOUR, "block_open_or_rush_four");
        if (p2 != null) {
            return p2;
        }
        ThreatHit p3 = findAttack(board, myChess);
        if (p3 != null) {
            return p3;
        }
        return findPlacement(board, oppChess, P4_BLOCK_LIVE_THREE, "block_live_three");
    }

    public ThreatHit findForcedThreat(int[][] board, int myChess, int oppChess) {
        ThreatHit hit = findBestThreat(board, myChess, oppChess);
        if (hit == null || hit.priority() > P2_BLOCK_FOUR) {
            return null;
        }
        return hit;
    }

    public ThreatHit findMustBlock(int[][] board, int myChess, int oppChess) {
        ThreatHit p1 = findPlacement(board, oppChess, P1_BLOCK_FIVE, "block_five");
        if (p1 != null) {
            return p1;
        }
        return findPlacement(board, oppChess, P2_BLOCK_FOUR, "block_open_or_rush_four");
    }

    public List<String> describeThreats(int[][] board, int chess, String prefix) {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] != 0 || !hasNeighbor(board, row, col)) {
                    continue;
                }
                board[row][col] = chess;
                PatternStats stats = analyze(board, row, col, chess);
                board[row][col] = 0;
                if (stats.isFive()) {
                    rows.add(prefix + "_five_at_(" + row + "," + col + ")");
                } else if (stats.openFours > 0) {
                    rows.add(prefix + "_open_four_at_(" + row + "," + col + ")");
                } else if (stats.rushFours > 0) {
                    rows.add(prefix + "_rush_four_at_(" + row + "," + col + ")");
                } else if (stats.isDoubleLiveThree()) {
                    rows.add(prefix + "_double_live_three_at_(" + row + "," + col + ")");
                } else if (stats.isLiveThree()) {
                    rows.add(prefix + "_live_three_at_(" + row + "," + col + ")");
                }
                if (rows.size() >= 8) {
                    return rows;
                }
            }
        }
        return rows;
    }

    public PatternStats analyze(int[][] board, int row, int col, int chess) {
        int fives = 0;
        int openFours = 0;
        int rushFours = 0;
        int liveThrees = 0;
        for (int[] direction : DIRECTIONS) {
            int forward = countDirection(board, row, col, chess, direction[0], direction[1]);
            int backward = countDirection(board, row, col, chess, -direction[0], -direction[1]);
            int length = forward + backward + 1;
            int openEnds = openEnd(board, row, col, chess, direction[0], direction[1], forward)
                    + openEnd(board, row, col, chess, -direction[0], -direction[1], backward);
            if (length >= 5) {
                fives++;
            } else if (length == 4 && openEnds == 2) {
                openFours++;
            } else if (length == 4 && openEnds == 1) {
                rushFours++;
            } else if (length == 3 && openEnds == 2) {
                liveThrees++;
            }
        }
        return new PatternStats(fives, openFours, rushFours, liveThrees);
    }

    public String reasonForPlacement(int[][] board, int row, int col, int myChess, int oppChess) {
        board[row][col] = myChess;
        PatternStats mine = analyze(board, row, col, myChess);
        board[row][col] = 0;
        if (mine.isFive()) {
            return "win_five";
        }
        if (mine.openFours > 0) {
            return "make_open_four";
        }
        if (mine.isDoubleLiveThree()) {
            return "make_double_live_three";
        }
        if (mine.rushFours > 0) {
            return "make_rush_four";
        }
        if (mine.isLiveThree()) {
            return "make_live_three";
        }
        board[row][col] = oppChess;
        PatternStats opp = analyze(board, row, col, oppChess);
        board[row][col] = 0;
        if (opp.isFive()) {
            return "block_five";
        }
        if (opp.isOpenOrRushFour()) {
            return "block_open_or_rush_four";
        }
        if (opp.isLiveThree()) {
            return "block_live_three";
        }
        return "heuristic";
    }

    private ThreatHit findAttack(int[][] board, int myChess) {
        ThreatHit openFour = null;
        ThreatHit doubleThree = null;
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] != 0 || !hasNeighbor(board, row, col)) {
                    continue;
                }
                board[row][col] = myChess;
                PatternStats stats = analyze(board, row, col, myChess);
                board[row][col] = 0;
                if (stats.openFours > 0 && openFour == null) {
                    openFour = new ThreatHit(P3_ATTACK, row, col, "make_open_four");
                } else if (stats.isDoubleLiveThree() && doubleThree == null) {
                    doubleThree = new ThreatHit(P3_ATTACK, row, col, "make_double_live_three");
                }
            }
        }
        return openFour != null ? openFour : doubleThree;
    }

    private ThreatHit findPlacement(int[][] board, int chess, int priority, String reason) {
        for (int row = 0; row < GameConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.BOARD_SIZE; col++) {
                if (board[row][col] != 0) {
                    continue;
                }
                if (priority >= P2_BLOCK_FOUR && !hasNeighbor(board, row, col)) {
                    continue;
                }
                board[row][col] = chess;
                PatternStats stats = analyze(board, row, col, chess);
                board[row][col] = 0;
                boolean matched = switch (priority) {
                    case P0_WIN, P1_BLOCK_FIVE -> stats.isFive();
                    case P2_BLOCK_FOUR -> stats.isOpenOrRushFour();
                    case P4_BLOCK_LIVE_THREE -> stats.isLiveThree();
                    default -> false;
                };
                if (matched) {
                    return new ThreatHit(priority, row, col, reason);
                }
            }
        }
        return null;
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

    private boolean inBoard(int row, int col) {
        return row >= 0 && row < GameConstants.BOARD_SIZE && col >= 0 && col < GameConstants.BOARD_SIZE;
    }
}
