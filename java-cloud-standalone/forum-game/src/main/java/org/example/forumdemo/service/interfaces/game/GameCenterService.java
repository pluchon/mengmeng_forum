package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.vo.game.GameCenterOverviewVO;

// 游戏中心业务接口
public interface GameCenterService {

    GameCenterOverviewVO getOverview(Long userId);
}
