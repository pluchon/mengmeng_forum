package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.bo.game.GameRankSettlementCommand;
import org.pluchon.forum.entity.bo.game.GameRankSettlementResult;
import org.pluchon.forum.entity.vo.game.GameRankInfoVO;

public interface GameRankService {

    /** 构建段位展示信息 */
    GameRankInfoVO buildRankInfo(String gameCode, Integer score);

    /** 结算真人 PK 排位分 */
    GameRankSettlementResult settleRank(GameRankSettlementCommand command);
}
