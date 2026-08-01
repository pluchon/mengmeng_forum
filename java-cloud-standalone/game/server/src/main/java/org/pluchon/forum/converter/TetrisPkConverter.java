package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.GameTetrisPkMatchRecord;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.game.TetrisPkLeaderboardVO;
import org.pluchon.forum.entity.vo.game.TetrisPkRecordVO;

// 俄罗斯方块 PK 实体转换器
public class TetrisPkConverter {

    private TetrisPkConverter() {
    }

    public static TetrisPkRecordVO toRecordVO(GameTetrisPkMatchRecord record, Long viewerUserId, String opponentNickname) {
        boolean isPlayer1 = viewerUserId != null && viewerUserId.equals(record.getPlayer1UserId());
        Long opponentUserId = isPlayer1 ? record.getPlayer2UserId() : record.getPlayer1UserId();
        int myScore = isPlayer1 ? value(record.getPlayer1Score()) : value(record.getPlayer2Score());
        int opponentScore = isPlayer1 ? value(record.getPlayer2Score()) : value(record.getPlayer1Score());
        int legacyDelta = record.getScoreDelta() == null ? 0 : record.getScoreDelta();
        int winnerDelta = record.getWinnerScoreDelta() == null ? legacyDelta : record.getWinnerScoreDelta();
        int loserDelta = record.getLoserScoreDelta() == null ? -legacyDelta : record.getLoserScoreDelta();
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
                win ? winnerDelta : loserDelta,
                winnerDelta,
                loserDelta,
                record.getEndReason(),
                record.getStartedAt(),
                record.getEndedAt()
        );
    }

    public static TetrisPkLeaderboardVO toLeaderboardVO(
            GameUserProfile profile,
            User user,
            Integer bestScore
    ) {
        int totalCount = value(profile.getTotalCount());
        int winCount = value(profile.getWinCount());
        int winRate = totalCount <= 0 ? 0 : (int) Math.round(winCount * 100.0 / totalCount);
        return new TetrisPkLeaderboardVO(
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                winRate,
                value(bestScore),
                totalCount,
                winCount
        );
    }

    private static int value(Integer n) {
        return n == null ? 0 : n;
    }
}
