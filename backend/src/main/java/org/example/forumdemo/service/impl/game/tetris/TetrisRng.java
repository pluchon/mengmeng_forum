package org.example.forumdemo.service.impl.game.tetris;

// 可复现伪随机，与前端 rng.js 保持一致
public final class TetrisRng {

    private long state;

    private TetrisRng(long seed) {
        this.state = (seed & 0xffffffffL);
        if (this.state == 0) {
            this.state = 1;
        }
    }

    public static TetrisRng create(long seed) {
        return new TetrisRng(seed);
    }

    // 与 JS Math.imul 一致：按 32 位有符号整数相乘并截断
    private static int imul(int a, int b) {
        return a * b;
    }

    public double nextDouble() {
        int s = (int) state;
        s += 0x6d2b79f5;
        state = s & 0xffffffffL;
        int t = s;
        t = imul(t ^ (t >>> 15), t | 1);
        t ^= t + imul(t ^ (t >>> 7), t | 61);
        return (Integer.toUnsignedLong(t ^ (t >>> 14))) / 4294967296.0;
    }

    public String pickBlockType() {
        int index = (int) Math.floor(nextDouble() * TetrisEngineConstants.BLOCK_TYPES.size());
        if (index >= TetrisEngineConstants.BLOCK_TYPES.size()) {
            index = TetrisEngineConstants.BLOCK_TYPES.size() - 1;
        }
        return TetrisEngineConstants.BLOCK_TYPES.get(index);
    }
}
