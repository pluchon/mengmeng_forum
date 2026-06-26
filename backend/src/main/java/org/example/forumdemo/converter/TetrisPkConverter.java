package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.GameTetrisPkMatchRecord;
import org.example.forumdemo.entity.vo.game.TetrisPkRecordVO;

// 俄罗斯方块 PK 实体转换器
public class TetrisPkConverter {

    private TetrisPkConverter() {
    }

    public static TetrisPkRecordVO toRecordVO(GameTetrisPkMatchRecord record, Long viewerUserId, String opponentNickname) {
        boolean isPlayer1 = viewerUserId != null && viewerUserId.equals(record.getPlayer1UserId());
        Long opponentUserId = isPlayer1 ? record.getPlayer2UserId() : record.getPlayer1UserId();
        int myScore = isPlayer1 ? value(record.getPlayer1Score()) : value(record.getPlayer2Score());
        int opponentScore = isPlayer1 ? value(record.getPlayer2Score()) : value(record.getPlayer1Score());
        int delta = record.getScoreDelta() == null ? 0 : record.getScoreDelta();
        boolean win = viewerUserId != null && viewerUserId.equals(record.getWinnerUserId());
        return new TetrisPkRecordVO(
                record.getId(),
                record.getRoomId(),
                viewerUserId,
                opponentUserId,
                opponentNickname,
                myScore,
                opponentScore,
                record.getWinnerUserId(),
                win ? delta : -delta,
                record.getEndReason(),
                record.getStartedAt(),
                record.getEndedAt()
        );
    }

    private static int value(Integer n) {
        return n == null ? 0 : n;
    }
}
