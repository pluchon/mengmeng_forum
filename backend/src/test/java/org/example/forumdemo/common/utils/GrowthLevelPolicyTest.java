package org.example.forumdemo.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 成长等级阈值策略单元测试
class GrowthLevelPolicyTest {

    @Test
    void calculatesNonLinearLevelsAtEveryBoundary() {
        assertEquals(1, GrowthLevelPolicy.calculateLevel(0));
        assertEquals(1, GrowthLevelPolicy.calculateLevel(99));
        assertEquals(2, GrowthLevelPolicy.calculateLevel(100));
        assertEquals(3, GrowthLevelPolicy.calculateLevel(250));
        assertEquals(4, GrowthLevelPolicy.calculateLevel(500));
        assertEquals(5, GrowthLevelPolicy.calculateLevel(900));
        assertEquals(6, GrowthLevelPolicy.calculateLevel(1500));
        assertEquals(6, GrowthLevelPolicy.calculateLevel(3000));
    }

    @Test
    void returnsCurrentAndNextThresholds() {
        assertEquals(250, GrowthLevelPolicy.currentLevelExperience(3));
        assertEquals(500, GrowthLevelPolicy.nextLevelExperience(3));
        assertEquals(1500, GrowthLevelPolicy.currentLevelExperience(6));
        assertEquals(1500, GrowthLevelPolicy.nextLevelExperience(6));
    }
}
