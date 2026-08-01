package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GameUserProfileVO;
import org.pluchon.forum.entity.vo.game.TetrisPkLeaderboardVO;
import org.pluchon.forum.entity.vo.game.TetrisPkRecordVO;
import org.pluchon.forum.entity.vo.game.TetrisPkReplayVO;

// 俄罗斯方块 PK 资料与历史服务
public interface TetrisPkService {

    GameUserProfileVO getProfile(Long userId);

    PageResult<TetrisPkRecordVO> listRecords(Long userId, Integer pageNum, Integer pageSize);

    PageResult<TetrisPkLeaderboardVO> listLeaderboard(Integer pageNum, Integer pageSize);

    TetrisPkReplayVO getReplay(Long userId, Long recordId);
}
