package org.example.forumdemo.service.impl.game.tetris;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 棋盘碰撞与消行工具
public final class TetrisMatrixUtil {

    private TetrisMatrixUtil() {
    }

    public static boolean canPlace(TetrisBlock block, String[][] matrix) {
        int[] xy = block.getXy();
        int[][] shape = block.getShape();
        int horizontal = shape[0].length;
        for (int rowOffset = 0; rowOffset < shape.length; rowOffset++) {
            int[] row = shape[rowOffset];
            for (int colOffset = 0; colOffset < row.length; colOffset++) {
                if (row[colOffset] == 0) {
                    continue;
                }
                if (xy[1] < 0) {
                    return false;
                }
                if (xy[1] + horizontal > TetrisEngineConstants.COLS) {
                    return false;
                }
                int targetRow = xy[0] + rowOffset;
                if (targetRow < 0) {
                    continue;
                }
                if (targetRow >= TetrisEngineConstants.ROWS) {
                    return false;
                }
                if (!isEmpty(matrix[targetRow][xy[1] + colOffset])) {
                    return false;
                }
            }
        }
        return true;
    }

    public static List<Integer> findClearLines(String[][] matrix) {
        List<Integer> clearLines = new ArrayList<>();
        for (int row = 0; row < TetrisEngineConstants.ROWS; row++) {
            boolean full = true;
            for (int col = 0; col < TetrisEngineConstants.COLS; col++) {
                if (isEmpty(matrix[row][col])) {
                    full = false;
                    break;
                }
            }
            if (full) {
                clearLines.add(row);
            }
        }
        return clearLines.isEmpty() ? null : clearLines;
    }

    public static boolean isOver(String[][] matrix) {
        for (int col = 0; col < TetrisEngineConstants.COLS; col++) {
            if (!isEmpty(matrix[0][col])) {
                return true;
            }
        }
        return false;
    }

    public static String[][] mergeBlock(String[][] matrix, TetrisBlock block) {
        String[][] next = copyMatrix(matrix);
        int[] xy = block.getXy();
        int[][] shape = block.getShape();
        for (int rowOffset = 0; rowOffset < shape.length; rowOffset++) {
            int[] row = shape[rowOffset];
            for (int colOffset = 0; colOffset < row.length; colOffset++) {
                if (row[colOffset] == 0) {
                    continue;
                }
                int targetRow = xy[0] + rowOffset;
                if (targetRow >= 0) {
                    next[targetRow][xy[1] + colOffset] = block.getType();
                }
            }
        }
        return next;
    }

    public static String[][] clearLineRows(String[][] matrix, List<Integer> lines) {
        Set<Integer> clearSet = new HashSet<>(lines);
        List<String[]> kept = new ArrayList<>();
        for (int row = 0; row < TetrisEngineConstants.ROWS; row++) {
            if (!clearSet.contains(row)) {
                kept.add(Arrays.copyOf(matrix[row], TetrisEngineConstants.COLS));
            }
        }
        int padCount = lines.size();
        String[][] next = new String[TetrisEngineConstants.ROWS][];
        for (int i = 0; i < padCount; i++) {
            next[i] = new String[TetrisEngineConstants.COLS];
            Arrays.fill(next[i], "");
        }
        for (int i = 0; i < kept.size(); i++) {
            next[padCount + i] = kept.get(i);
        }
        return next;
    }

    public static TetrisBlock ghostDrop(TetrisBlock block, String[][] matrix) {
        TetrisBlock ghost = block.cloneBlock();
        TetrisBlock next = ghost.fall(1);
        while (canPlace(next, matrix)) {
            ghost = next;
            next = ghost.fall(1);
        }
        return ghost;
    }

    public static String[][] copyMatrix(String[][] matrix) {
        String[][] copy = new String[TetrisEngineConstants.ROWS][];
        for (int row = 0; row < TetrisEngineConstants.ROWS; row++) {
            copy[row] = Arrays.copyOf(matrix[row], TetrisEngineConstants.COLS);
        }
        return copy;
    }

    private static boolean isEmpty(String cell) {
        return cell == null || cell.isEmpty();
    }
}
