package org.pluchon.forum.service.impl.game.tetris;

import java.util.Arrays;

// 下落方块实例，与前端 block.js 行为一致
public class TetrisBlock {

    // 方块类型
    private final String type;

    // 当前形状矩阵
    private final int[][] shape;

    // 棋盘坐标 [row, col]
    private final int[] xy;

    // 旋转索引
    private final int rotateIndex;

    public TetrisBlock(String type, int[][] shape, int[] xy, int rotateIndex) {
        this.type = type;
        this.shape = deepCopy(shape);
        this.xy = Arrays.copyOf(xy, 2);
        this.rotateIndex = rotateIndex;
    }

    public static TetrisBlock spawn(String type) {
        int[][] shape = TetrisEngineConstants.BLOCK_SHAPE.get(type);
        int[] xy = "I".equals(type) ? new int[]{0, 3} : new int[]{-1, 4};
        return new TetrisBlock(type, shape, xy, 0);
    }

    public TetrisBlock rotate() {
        int[][] rotated = rotateShape(shape);
        int[][] kicks = TetrisEngineConstants.ORIGIN_KICKS.get(type);
        int[] kick = kicks[rotateIndex % kicks.length];
        int nextRotateIndex = rotateIndex + 1 >= kicks.length ? 0 : rotateIndex + 1;
        return new TetrisBlock(type, rotated, new int[]{xy[0] + kick[0], xy[1] + kick[1]}, nextRotateIndex);
    }

    public TetrisBlock fall(int steps) {
        return new TetrisBlock(type, shape, new int[]{xy[0] + steps, xy[1]}, rotateIndex);
    }

    public TetrisBlock right() {
        return new TetrisBlock(type, shape, new int[]{xy[0], xy[1] + 1}, rotateIndex);
    }

    public TetrisBlock left() {
        return new TetrisBlock(type, shape, new int[]{xy[0], xy[1] - 1}, rotateIndex);
    }

    public TetrisBlock cloneBlock() {
        return new TetrisBlock(type, shape, xy, rotateIndex);
    }

    public String getType() {
        return type;
    }

    public int[][] getShape() {
        return deepCopy(shape);
    }

    public int[] getXy() {
        return Arrays.copyOf(xy, 2);
    }

    public int getRotateIndex() {
        return rotateIndex;
    }

    private static int[][] rotateShape(int[][] shape) {
        int rows = shape.length;
        int cols = shape[0].length;
        int[][] result = new int[cols][rows];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                result[cols - col - 1][row] = shape[row][col];
            }
        }
        return result;
    }

    private static int[][] deepCopy(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }
}
