package org.pluchon.forum.service.impl.game;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import org.pluchon.forum.entity.vo.game.GobangBoardPointVO;

// 五子棋规则引擎，服务端权威判定落子与五连
@Component
public class GobangRuleEngine {

    public boolean inBoard(Integer row, Integer col) {
        return row != null
                && col != null
                && row >= 0
                && row < GameConstants.BOARD_SIZE
                && col >= 0
                && col < GameConstants.BOARD_SIZE;
    }

    public boolean hasFive(int[][] board, int chess, int row, int col) {
        int[][] axes = {
                {0, 1},
                {1, 0},
                {1, 1},
                {1, -1}
        };
        for (int[] axis : axes) {
            int total = 1
                    + count(board, chess, row, col, axis[0], axis[1])
                    + count(board, chess, row, col, -axis[0], -axis[1]);
            if (total >= 5) {
                return true;
            }
        }
        return false;
    }

    public List<GobangBoardPointVO> winningLine(int[][] board, int chess, int row, int col) {
        int[][] axes = {
                {0, 1},
                {1, 0},
                {1, 1},
                {1, -1}
        };
        for (int[] axis : axes) {
            List<GobangBoardPointVO> line = collectLine(board, chess, row, col, axis[0], axis[1]);
            if (line.size() >= 5) {
                return line.size() == 5 ? line : centeredFive(line, row, col);
            }
        }
        return List.of();
    }

    private List<GobangBoardPointVO> collectLine(int[][] board, int chess, int row, int col, int dr, int dc) {
        List<GobangBoardPointVO> line = new ArrayList<>();
        int startRow = row;
        int startCol = col;
        while (inBoard(startRow - dr, startCol - dc) && board[startRow - dr][startCol - dc] == chess) {
            startRow -= dr;
            startCol -= dc;
        }
        int nextRow = startRow;
        int nextCol = startCol;
        while (inBoard(nextRow, nextCol) && board[nextRow][nextCol] == chess) {
            line.add(new GobangBoardPointVO(nextRow, nextCol));
            nextRow += dr;
            nextCol += dc;
        }
        return line;
    }

    private List<GobangBoardPointVO> centeredFive(List<GobangBoardPointVO> line, int row, int col) {
        int moveIndex = 0;
        for (int i = 0; i < line.size(); i++) {
            GobangBoardPointVO point = line.get(i);
            if (point.getRow() == row && point.getCol() == col) {
                moveIndex = i;
                break;
            }
        }
        int start = Math.max(0, Math.min(moveIndex - 2, line.size() - 5));
        return new ArrayList<>(line.subList(start, start + 5));
    }

    private int count(int[][] board, int chess, int row, int col, int dr, int dc) {
        int result = 0;
        int nextRow = row + dr;
        int nextCol = col + dc;
        while (nextRow >= 0
                && nextRow < GameConstants.BOARD_SIZE
                && nextCol >= 0
                && nextCol < GameConstants.BOARD_SIZE
                && board[nextRow][nextCol] == chess) {
            result++;
            nextRow += dr;
            nextCol += dc;
        }
        return result;
    }
}
