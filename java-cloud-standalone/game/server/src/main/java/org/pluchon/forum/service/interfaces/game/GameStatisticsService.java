package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GameStatisticsRecordVO;
import org.pluchon.forum.entity.vo.game.GameStatisticsSummaryVO;

// 游戏中心统一对局统计服务
public interface GameStatisticsService {

    GameStatisticsSummaryVO getSummary(Long userId);

    PageResult<GameStatisticsRecordVO> listRecords(Long userId, String gameCode, Integer pageNum, Integer pageSize);
}
