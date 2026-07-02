package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.bo.game.GameRankSettlementCommand;
import org.example.forumdemo.entity.bo.game.GameRankSettlementResult;
import org.example.forumdemo.entity.vo.game.GameRankInfoVO;

public interface GameRankService {

    /** 构建段位展示信息 */
    GameRankInfoVO buildRankInfo(String gameCode, Integer score);

    /** 结算真人 PK 排位分 */
    GameRankSettlementResult settleRank(GameRankSettlementCommand command);
}
