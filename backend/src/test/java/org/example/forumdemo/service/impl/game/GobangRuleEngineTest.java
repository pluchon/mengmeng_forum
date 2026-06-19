package org.example.forumdemo.service.impl.game;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GobangRuleEngineTest {

    private final GobangRuleEngine engine = new GobangRuleEngine();

    @Test
    void shouldDetectHorizontalFive() {
        int[][] board = new int[GameConstants.BOARD_SIZE][GameConstants.BOARD_SIZE];
        for (int col = 3; col <= 7; col++) {
            board[7][col] = 1;
        }
        Assertions.assertTrue(engine.hasFive(board, 1, 7, 5));
    }

    @Test
    void shouldDetectVerticalFive() {
        int[][] board = new int[GameConstants.BOARD_SIZE][GameConstants.BOARD_SIZE];
        for (int row = 2; row <= 6; row++) {
            board[row][4] = 2;
        }
        Assertions.assertTrue(engine.hasFive(board, 2, 4, 4));
    }

    @Test
    void shouldDetectMainDiagonalFive() {
        int[][] board = new int[GameConstants.BOARD_SIZE][GameConstants.BOARD_SIZE];
        for (int offset = 0; offset < 5; offset++) {
            board[3 + offset][5 + offset] = 1;
        }
        Assertions.assertTrue(engine.hasFive(board, 1, 5, 7));
    }

    @Test
    void shouldDetectAntiDiagonalFive() {
        int[][] board = new int[GameConstants.BOARD_SIZE][GameConstants.BOARD_SIZE];
        for (int offset = 0; offset < 5; offset++) {
            board[8 - offset][3 + offset] = 2;
        }
        Assertions.assertTrue(engine.hasFive(board, 2, 6, 5));
    }

    @Test
    void shouldRejectOutOfBoardPosition() {
        Assertions.assertFalse(engine.inBoard(-1, 0));
        Assertions.assertFalse(engine.inBoard(0, 15));
        Assertions.assertTrue(engine.inBoard(14, 14));
    }
}
