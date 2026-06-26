package org.example.forumdemo.service.impl.game;

import org.example.forumdemo.entity.vo.game.JinziBoardPointVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 井字棋规则引擎，服务端权威判定落子、三连和平局
@Component
public class JinziRuleEngine {

    public boolean inBoard(Integer row, Integer col) {
        return row != null
                && col != null
                && row >= 0
                && row < GameConstants.JINZI_BOARD_SIZE
                && col >= 0
                && col < GameConstants.JINZI_BOARD_SIZE;
    }

    public boolean hasLine(int[][] board, int chess) {
        return !winningLine(board, chess).isEmpty();
    }

    public List<JinziBoardPointVO> winningLine(int[][] board, int chess) {
        List<List<JinziBoardPointVO>> lines = List.of(
                line(0, 0, 0, 1, 0, 2),
                line(1, 0, 1, 1, 1, 2),
                line(2, 0, 2, 1, 2, 2),
                line(0, 0, 1, 0, 2, 0),
                line(0, 1, 1, 1, 2, 1),
                line(0, 2, 1, 2, 2, 2),
                line(0, 0, 1, 1, 2, 2),
                line(0, 2, 1, 1, 2, 0)
        );
        for (List<JinziBoardPointVO> line : lines) {
            boolean matched = true;
            for (JinziBoardPointVO point : line) {
                if (board[point.getRow()][point.getCol()] != chess) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return line;
            }
        }
        return List.of();
    }

    public boolean isDraw(int[][] board) {
        for (int row = 0; row < GameConstants.JINZI_BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.JINZI_BOARD_SIZE; col++) {
                if (board[row][col] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<JinziBoardPointVO> line(int rowA, int colA, int rowB, int colB, int rowC, int colC) {
        List<JinziBoardPointVO> result = new ArrayList<>(3);
        result.add(new JinziBoardPointVO(rowA, colA));
        result.add(new JinziBoardPointVO(rowB, colB));
        result.add(new JinziBoardPointVO(rowC, colC));
        return result;
    }
}
