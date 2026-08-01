package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.dto.game.TetrisSettleRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.TetrisProfileVO;
import org.pluchon.forum.entity.vo.game.TetrisRecordVO;
import org.pluchon.forum.entity.vo.game.TetrisReplayVO;
import org.pluchon.forum.entity.vo.game.TetrisSettleResultVO;

// 俄罗斯方块单人模式服务
public interface TetrisService {

    TetrisProfileVO getProfile(Long userId);

    PageResult<TetrisRecordVO> listRecords(Long userId, Integer pageNum, Integer pageSize);

    PageResult<TetrisProfileVO> listLeaderboard(Integer pageNum, Integer pageSize);

    TetrisSettleResultVO settle(Long userId, TetrisSettleRequest request);

    TetrisReplayVO getReplay(Long userId, Long recordId);
}
