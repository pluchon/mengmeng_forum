package org.pluchon.forum.service.impl.game.tetris;

import java.util.List;

// 单个玩家的权威棋盘状态，服务端推进下落与消行
//
// 竞速模式下两人各打各的：这里不产生也不接收垃圾行，胜负由房间按消行数与分数裁定。
public class TetrisPlayerState {

    // 落锁后等待消行/生成下一块的毫秒数，与前端 useTetrisEngine 一致
    private static final int LOCK_DELAY_MS = 100;

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

    // 连续消行连击数（零消放置后归零）
    private int combo;

    // 当前下落速度档位
    private int speedRun;

    // 初始速度档位
    private final int speedStart;

    // 是否锁定中 消行动画
    private boolean lock;

    // 是否已结束
    private boolean gameOver;

    // 随机种子
    private final long seed;

    // 随机数生成器
    private final TetrisRng rng;

    // 下次自动下落时间戳
    private long nextFallAtMs;

    // 落锁后延迟消行/生成
    private boolean pendingLockComplete;

    // 落锁完成时间戳
    private long lockUntilMs;

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

    public int getCombo() {
        return combo;
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

    // 落锁延迟结束后完成消行并生成下一块，返回本次是否推进了状态
    public boolean advanceLockIfReady(long nowMs) {
        if (!pendingLockComplete || nowMs < lockUntilMs) {
            return false;
        }
        completePendingLock(nowMs);
        return true;
    }

    // 处理玩家输入，返回本次是否改变了棋盘（决定要不要广播）
    public boolean handleInput(String action, long nowMs) {
        if (gameOver) {
            return false;
        }
        if (pendingLockComplete || lock || cur == null) {
            return false;
        }
        return switch (action) {
            case "left" -> moveHorizontal(true);
            case "right" -> moveHorizontal(false);
            case "down" -> softFall(nowMs);
            case "rotate" -> rotatePiece();
            case "space" -> hardDrop(nowMs);
            case "hold" -> holdPiece();
            default -> false;
        };
    }

    // 服务端重力 tick，返回本次是否改变了棋盘
    public boolean tickFall(long nowMs) {
        if (gameOver) {
            return false;
        }
        if (advanceLockIfReady(nowMs)) {
            return true;
        }
        if (pendingLockComplete || lock || cur == null) {
            return false;
        }
        if (nowMs < nextFallAtMs) {
            return false;
        }
        return softFall(nowMs);
    }

    private boolean hardDrop(long nowMs) {
        int index = 0;
        TetrisBlock bottom = cur.fall(index);
        while (TetrisMatrixUtil.canPlace(bottom, matrix)) {
            index += 1;
            bottom = cur.fall(index);
        }
        cur = cur.fall(Math.max(0, index - 1));
        lockCurrent(nowMs);
        return true;
    }

    private boolean moveHorizontal(boolean left) {
        TetrisBlock next = left ? cur.left() : cur.right();
        if (!TetrisMatrixUtil.canPlace(next, matrix)) {
            return false;
        }
        cur = next;
        return true;
    }

    private boolean rotatePiece() {
        TetrisBlock next = cur.rotate();
        if (!TetrisMatrixUtil.canPlace(next, matrix)) {
            return false;
        }
        cur = next;
        return true;
    }

    private boolean softFall(long nowMs) {
        TetrisBlock next = cur.fall(1);
        if (TetrisMatrixUtil.canPlace(next, matrix)) {
            cur = next;
            scheduleFall(nowMs);
            return true;
        }
        lockCurrent(nowMs);
        return true;
    }

    private boolean holdPiece() {
        if (!canHold) {
            return false;
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
        }
        return true;
    }

    private void lockCurrent(long nowMs) {
        String[][] merged = TetrisMatrixUtil.mergeBlock(matrix, cur);
        cur = null;
        lock = true;
        matrix = merged;
        points += 10 + (speedRun - 1) * 2;
        canHold = true;
        pendingLockComplete = true;
        lockUntilMs = nowMs + LOCK_DELAY_MS;
    }

    private void completePendingLock(long nowMs) {
        pendingLockComplete = false;
        List<Integer> lines = TetrisMatrixUtil.findClearLines(matrix);
        if (lines != null) {
            matrix = TetrisMatrixUtil.clearLineRows(matrix, lines);
            clearLines += lines.size();
            combo += 1;
            points += TetrisEngineConstants.calcClearScore(lines.size(), combo);
            int speedAdd = clearLines / TetrisEngineConstants.EACH_LINES;
            speedRun = Math.min(6, speedStart + speedAdd);
            spawnAfterLock(nowMs);
            return;
        }

        combo = 0;

        if (TetrisMatrixUtil.isOver(matrix)) {
            finishGame();
            lock = false;
            return;
        }
        spawnAfterLock(nowMs);
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
        pendingLockComplete = false;
    }

    private void scheduleFall(long nowMs) {
        int speedIndex = Math.max(0, Math.min(speedRun - 1, TetrisEngineConstants.SPEEDS_MS.length - 1));
        nextFallAtMs = nowMs + TetrisEngineConstants.SPEEDS_MS[speedIndex];
    }
}
