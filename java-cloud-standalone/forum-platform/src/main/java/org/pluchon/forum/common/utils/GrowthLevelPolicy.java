package org.pluchon.forum.common.utils;

// 成长等级阈值策略
public final class GrowthLevelPolicy {

    private static final int[] LEVEL_THRESHOLDS = {0, 100, 250, 500, 900, 1500};

    private GrowthLevelPolicy() {
    }

    // 根据累计经验计算等级
    public static int calculateLevel(Integer experience) {
        int validExperience = Math.max(0, experience == null ? 0 : experience);
        for (int index = LEVEL_THRESHOLDS.length - 1; index >= 0; index--) {
            if (validExperience >= LEVEL_THRESHOLDS[index]) {
                return index + 1;
            }
        }
        return 1;
    }

    // 查询当前等级起始经验
    public static int currentLevelExperience(Integer level) {
        int validLevel = normalizeLevel(level);
        return LEVEL_THRESHOLDS[validLevel - 1];
    }

    // 查询下一等级经验阈值
    public static int nextLevelExperience(Integer level) {
        int validLevel = normalizeLevel(level);
        if (validLevel >= LEVEL_THRESHOLDS.length) {
            return LEVEL_THRESHOLDS[LEVEL_THRESHOLDS.length - 1];
        }
        return LEVEL_THRESHOLDS[validLevel];
    }

    private static int normalizeLevel(Integer level) {
        int validLevel = level == null ? 1 : level;
        return Math.min(LEVEL_THRESHOLDS.length, Math.max(1, validLevel));
    }
}
