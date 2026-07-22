package org.example.forumdemo.service.impl.lottery;

import org.example.forumdemo.common.constant.Constant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LotteryPityPolicyTest {

    @Test
    void missShouldIncreasePityUntilThreshold() {
        assertEquals(1, LotteryServiceImpl.nextPityAfterMiss(0));
        assertEquals(Constant.LOTTERY_HARD_PITY_AFTER_MISSES,
                LotteryServiceImpl.nextPityAfterMiss(Constant.LOTTERY_HARD_PITY_AFTER_MISSES - 1));
    }

    @Test
    void soldOutJackpotMissShouldNotIncreasePityBeyondThreshold() {
        assertEquals(Constant.LOTTERY_HARD_PITY_AFTER_MISSES,
                LotteryServiceImpl.nextPityAfterMiss(Constant.LOTTERY_HARD_PITY_AFTER_MISSES));
        assertEquals(Constant.LOTTERY_HARD_PITY_AFTER_MISSES,
                LotteryServiceImpl.nextPityAfterMiss(Constant.LOTTERY_HARD_PITY_AFTER_MISSES + 20));
    }
}
