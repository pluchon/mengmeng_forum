package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.game.GameCenterOverviewVO;

// 游戏中心业务接口
public interface GameCenterService {

    GameCenterOverviewVO getOverview(Long userId);
}
