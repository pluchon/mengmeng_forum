package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GameCategoryVO;
import org.pluchon.forum.entity.vo.game.GameCenterOverviewVO;
import org.pluchon.forum.entity.vo.game.GameDefinitionVO;

import java.util.List;

// 游戏中心业务接口
public interface GameCenterService {

    GameCenterOverviewVO getOverview(Long userId);

    PageResult<GameDefinitionVO> pageGames(Integer pageNum, Integer pageSize, String category);

    List<GameCategoryVO> listCategories();
}
