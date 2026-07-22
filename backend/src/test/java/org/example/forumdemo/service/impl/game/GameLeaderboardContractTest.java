package org.example.forumdemo.service.impl.game;

import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.TetrisPkService;
import org.example.forumdemo.service.interfaces.game.TetrisService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// 游戏排行榜分页与删除范围契约测试
class GameLeaderboardContractTest {

    @Test
    void tetrisLeaderboardsShouldReturnBackendPages() throws Exception {
        Method solo = TetrisService.class.getMethod("listLeaderboard", Integer.class, Integer.class);
        Method pk = TetrisPkService.class.getMethod("listLeaderboard", Integer.class, Integer.class);

        assertEquals(PageResult.class, solo.getReturnType());
        assertEquals(PageResult.class, pk.getReturnType());
    }

    @Test
    void genericGobangAndJinziLeaderboardServiceShouldBeRemoved() {
        boolean remains = Arrays.stream(GameUserProfileService.class.getMethods())
                .anyMatch(method -> method.getName().equals("listLeaderboard"));

        assertFalse(remains);
    }
}
