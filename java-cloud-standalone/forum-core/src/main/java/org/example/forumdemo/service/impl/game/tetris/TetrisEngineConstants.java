package org.example.forumdemo.service.impl.game.tetris;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 俄罗斯方块引擎常量，与前端单人模式保持一致
public final class TetrisEngineConstants {

    public static final int ROWS = 20;

    public static final int COLS = 10;

    public static final String GARBAGE_TYPE = "G";

    public static final List<String> BLOCK_TYPES = List.of("I", "O", "T", "S", "Z", "J", "L");

    public static final Map<String, int[][]> BLOCK_SHAPE;

    public static final Map<String, int[][]> ORIGIN_KICKS;

    public static final int[] SPEEDS_MS = {800, 650, 500, 370, 250, 160};

    public static final int[] CLEAR_POINTS = {100, 300, 700, 1500};

    public static final int EACH_LINES = 20;

    static {
        Map<String, int[][]> shapes = new HashMap<>();
        shapes.put("I", new int[][]{{1, 1, 1, 1}});
        shapes.put("L", new int[][]{{0, 0, 1}, {1, 1, 1}});
        shapes.put("J", new int[][]{{1, 0, 0}, {1, 1, 1}});
        shapes.put("Z", new int[][]{{1, 1, 0}, {0, 1, 1}});
        shapes.put("S", new int[][]{{0, 1, 1}, {1, 1, 0}});
        shapes.put("O", new int[][]{{1, 1}, {1, 1}});
        shapes.put("T", new int[][]{{0, 1, 0}, {1, 1, 1}});
        BLOCK_SHAPE = Collections.unmodifiableMap(shapes);

        Map<String, int[][]> kicks = new HashMap<>();
        kicks.put("I", new int[][]{{-1, 1}, {1, -1}});
        kicks.put("L", new int[][]{{0, 0}});
        kicks.put("J", new int[][]{{0, 0}});
        kicks.put("Z", new int[][]{{0, 0}});
        kicks.put("S", new int[][]{{0, 0}});
        kicks.put("O", new int[][]{{0, 0}});
        kicks.put("T", new int[][]{{0, 0}, {1, 0}, {-1, 1}, {0, -1}});
        ORIGIN_KICKS = Collections.unmodifiableMap(kicks);
    }

    private TetrisEngineConstants() {
    }

    public static String[][] createBlankMatrix() {
        String[][] matrix = new String[ROWS][COLS];
        for (int row = 0; row < ROWS; row++) {
            matrix[row] = new String[COLS];
            Arrays.fill(matrix[row], "");
        }
        return matrix;
    }

    // PK 消行与单人模式一致：只更新己方棋盘，不向对手发送垃圾行
    public static int garbageLinesForClear(int lines) {
        return 0;
    }
}
