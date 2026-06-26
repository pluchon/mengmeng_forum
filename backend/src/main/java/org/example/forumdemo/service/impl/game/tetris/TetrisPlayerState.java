package org.example.forumdemo.service.impl.game.tetris;

import java.util.List;
import java.util.Random;

// 单个玩家的权威棋盘状态，服务端推进下落与消行
public class TetrisPlayerState {

    // 棋盘矩阵
    private String[][] matrix;

    // 当前下落方块
    private TetrisBlock cur;

    // 下一个方块类型
    private String nextType;

    // 暂存方块类型
    private String holdType;

    // 本局是否还能 hold
    private boolean canHold;

    // 分数
    private int points;

    // 消行数
    private int clearLines;

    // 当前下落速度档位
    private int speedRun;

    // 初始速度档位
    private final int speedStart;

    // 是否锁定中（消行动画）
    private boolean lock;

    // 是否已结束
    private boolean gameOver;

    // 随机种子
    private final long seed;

    // 随机数生成器
    private final TetrisRng rng;

    // 下次自动下落时间戳
    private long nextFallAtMs;

    public TetrisPlayerState(long seed) {
        this.seed = seed;
        this.rng = TetrisRng.create(seed);
        this.matrix = TetrisMatrixUtil.copyMatrix(TetrisEngineConstants.createBlankMatrix());
        this.speedStart = 1;
        this.speedRun = 1;
        this.canHold = true;
        this.nextType = drawNextType();
        spawnNext();
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getPoints() {
        return points;
    }

    public int getClearLines() {
        return clearLines;
    }

    public String[][] getMatrix() {
        return TetrisMatrixUtil.copyMatrix(matrix);
    }

    public TetrisBlock getCur() {
        return cur == null ? null : cur.cloneBlock();
    }

    public String getNextType() {
        return nextType;
    }

    public String getHoldType() {
        return holdType;
    }

    public long getSeed() {
        return seed;
    }

    // 处理玩家输入，返回本次消行后应给对手增加的垃圾行数
    public int handleInput(String action, long nowMs) {
        if (gameOver || lock || cur == null) {
            return 0;
        }
        return switch (action) {
            case "left" -> {
                moveHorizontal(true);
                yield 0;
            }
            case "right" -> {
                moveHorizontal(false);
                yield 0;
            }
            case "down" -> {
                softFall(false, nowMs);
                yield 0;
            }
            case "rotate" -> {
                rotatePiece();
                yield 0;
            }
            case "space" -> hardDrop(nowMs);
            case "hold" -> {
                holdPiece(nowMs);
                yield 0;
            }
            default -> 0;
        };
    }

    // 服务端重力 tick
    public int tickFall(long nowMs) {
        if (gameOver || lock || cur == null) {
            return 0;
        }
        if (nowMs < nextFallAtMs) {
            return 0;
        }
        softFall(true, nowMs);
        return 0;
    }

    public void addGarbageLines(int count, Random random) {
        if (gameOver || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            if (gameOver) {
                return;
            }
            int hole = random.nextInt(TetrisEngineConstants.COLS);
            if (TetrisMatrixUtil.isOver(matrix)) {
                gameOver = true;
                cur = null;
                return;
            }
            String[][] next = TetrisMatrixUtil.copyMatrix(matrix);
            for (int row = 0; row < TetrisEngineConstants.ROWS - 1; row++) {
                next[row] = next[row + 1];
            }
            String[] garbageRow = new String[TetrisEngineConstants.COLS];
            for (int col = 0; col < TetrisEngineConstants.COLS; col++) {
                garbageRow[col] = col == hole ? "" : TetrisEngineConstants.GARBAGE_TYPE;
            }
            next[TetrisEngineConstants.ROWS - 1] = garbageRow;
            matrix = next;
            if (TetrisMatrixUtil.isOver(matrix)) {
                gameOver = true;
                cur = null;
            }
        }
    }

    private int hardDrop(long nowMs) {
        int index = 0;
        TetrisBlock bottom = cur.fall(index);
        while (TetrisMatrixUtil.canPlace(bottom, matrix)) {
            index += 1;
            bottom = cur.fall(index);
        }
        cur = cur.fall(Math.max(0, index - 1));
        return lockCurrent(nowMs);
    }

    private void moveHorizontal(boolean left) {
        TetrisBlock next = left ? cur.left() : cur.right();
        if (TetrisMatrixUtil.canPlace(next, matrix)) {
            cur = next;
        }
    }

    private void rotatePiece() {
        TetrisBlock next = cur.rotate();
        if (TetrisMatrixUtil.canPlace(next, matrix)) {
            cur = next;
        }
    }

    private void softFall(boolean auto, long nowMs) {
        TetrisBlock next = cur.fall(1);
        if (TetrisMatrixUtil.canPlace(next, matrix)) {
            cur = next;
            scheduleFall(nowMs);
            return;
        }
        lockCurrent(nowMs);
    }

    private void holdPiece(long nowMs) {
        if (!canHold) {
            return;
        }
        String currentType = cur.getType();
        if (holdType != null && !holdType.isEmpty()) {
            cur = TetrisBlock.spawn(holdType);
        } else {
            cur = TetrisBlock.spawn(nextType);
            nextType = drawNextType();
        }
        holdType = currentType;
        canHold = false;
        if (!TetrisMatrixUtil.canPlace(cur, matrix)) {
            finishGame();
            return;
        }
        scheduleFall(nowMs);
    }

    private int lockCurrent(long nowMs) {
        String[][] merged = TetrisMatrixUtil.mergeBlock(matrix, cur);
        cur = null;
        lock = true;
        matrix = merged;
        points += 10 + (speedRun - 1) * 2;
        canHold = true;

        List<Integer> lines = TetrisMatrixUtil.findClearLines(matrix);
        if (lines != null) {
            matrix = TetrisMatrixUtil.clearLineRows(matrix, lines);
            clearLines += lines.size();
            points += TetrisEngineConstants.CLEAR_POINTS[lines.size() - 1];
            int speedAdd = clearLines / TetrisEngineConstants.EACH_LINES;
            speedRun = Math.min(6, speedStart + speedAdd);
            int garbage = TetrisEngineConstants.garbageLinesForClear(lines.size());
            spawnAfterLock(nowMs);
            return garbage;
        }

        if (TetrisMatrixUtil.isOver(matrix)) {
            finishGame();
            lock = false;
            return 0;
        }
        spawnAfterLock(nowMs);
        return 0;
    }

    private void spawnAfterLock(long nowMs) {
        spawnNext();
        lock = false;
        scheduleFall(nowMs);
    }

    private void spawnNext() {
        String type = nextType == null || nextType.isEmpty() ? drawNextType() : nextType;
        nextType = drawNextType();
        cur = TetrisBlock.spawn(type);
        if (!TetrisMatrixUtil.canPlace(cur, matrix)) {
            finishGame();
        }
    }

    private String drawNextType() {
        return rng.pickBlockType();
    }

    private void finishGame() {
        gameOver = true;
        cur = null;
        lock = false;
    }

    private void scheduleFall(long nowMs) {
        int speedIndex = Math.max(0, Math.min(speedRun - 1, TetrisEngineConstants.SPEEDS_MS.length - 1));
        nextFallAtMs = nowMs + TetrisEngineConstants.SPEEDS_MS[speedIndex];
    }
}
