package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.GameDefinition;
import org.pluchon.forum.entity.db.GameGobangMatchRecord;
import org.pluchon.forum.entity.db.GameGobangRoomMove;
import org.pluchon.forum.entity.db.GameJinziMatchRecord;
import org.pluchon.forum.entity.db.GameJinziRoomMove;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.service.impl.game.GameConstants;
import org.pluchon.forum.entity.vo.game.GameDefinitionVO;
import org.pluchon.forum.entity.vo.game.GameMatchRecordVO;
import org.pluchon.forum.entity.vo.game.GameUserProfileVO;
import org.pluchon.forum.entity.vo.game.GobangMoveVO;

// 游戏模块实体转换器，避免 Controller 直接返回数据库实体
public class GameConverter {

    private GameConverter() {
    }

    public static GameDefinitionVO toDefinitionVO(GameDefinition row, int onlineCount) {
        return new GameDefinitionVO(
                row.getGameCode(),
                row.getGameName(),
                row.getCoverUrl(),
                row.getStatus() != null && row.getStatus() == 1,
                onlineCount
        );
    }

    public static GameUserProfileVO toProfileVO(GameUserProfile profile, User user) {
        int total = value(profile.getTotalCount());
        int wins = value(profile.getWinCount());
        int winRate = total == 0 ? 0 : (int) Math.round(wins * 100.0 / total);
        var rankInfo = org.pluchon.forum.service.impl.game.GameRankRules.buildRankInfo(
                profile.getGameCode(),
                profile.getScore()
        );
        return new GameUserProfileVO(
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                profile.getGameCode(),
                value(profile.getScore()),
                rankInfo,
                rankInfo.getRankName(),
                rankInfo.getNextRankDistance(),
                user == null ? 0 : value(user.getPoints()),
                total,
                wins,
                value(profile.getLoseCount()),
                value(profile.getDrawCount()),
                winRate,
                profile.getCurrentStatus(),
                profile.getCurrentRoomId()
        );
    }

    public static GameMatchRecordVO toGobangRecordVO(GameGobangMatchRecord row) {
        return toGobangRecordVO(row, null);
    }

    public static GameMatchRecordVO toGobangRecordVO(GameGobangMatchRecord row, Long viewerUserId) {
        return toRecordVO(GameConstants.GOBANG, row.getId(), row.getRoomId(), row.getBlackUserId(),
                row.getWhiteUserId(), row.getWinnerUserId(), row.getLoserUserId(), row.getEndReason(),
                row.getScoreDelta(), row.getWinnerScoreDelta(), row.getLoserScoreDelta(), viewerUserId,
                row.getStartedAt(), row.getEndedAt());
    }

    public static GameMatchRecordVO toJinziRecordVO(GameJinziMatchRecord row) {
        return toJinziRecordVO(row, null);
    }

    public static GameMatchRecordVO toJinziRecordVO(GameJinziMatchRecord row, Long viewerUserId) {
        return toRecordVO(GameConstants.JINZI, row.getId(), row.getRoomId(), row.getBlackUserId(),
                row.getWhiteUserId(), row.getWinnerUserId(), row.getLoserUserId(), row.getEndReason(),
                row.getScoreDelta(), row.getWinnerScoreDelta(), row.getLoserScoreDelta(), viewerUserId,
                row.getStartedAt(), row.getEndedAt());
    }

    public static GobangMoveVO toGobangMoveVO(GameGobangRoomMove row) {
        return toMoveVO(row.getUserId(), row.getRowIndex(), row.getColIndex(), row.getChess());
    }

    public static GobangMoveVO toJinziMoveVO(GameJinziRoomMove row) {
        return toMoveVO(row.getUserId(), row.getRowIndex(), row.getColIndex(), row.getChess());
    }

    private static GameMatchRecordVO toRecordVO(
            String gameCode,
            Long id,
            String roomId,
            Long blackUserId,
            Long whiteUserId,
            Long winnerUserId,
            Long loserUserId,
            String endReason,
            Integer scoreDelta,
            Integer winnerScoreDelta,
            Integer loserScoreDelta,
            Long viewerUserId,
            java.util.Date startedAt,
            java.util.Date endedAt
    ) {
        int legacyDelta = value(scoreDelta);
        int safeWinnerDelta = winnerScoreDelta == null ? legacyDelta : winnerScoreDelta;
        int safeLoserDelta = loserScoreDelta == null ? -legacyDelta : loserScoreDelta;
        return new GameMatchRecordVO(
                id,
                gameCode,
                roomId,
                blackUserId,
                whiteUserId,
                winnerUserId,
                loserUserId,
                endReason,
                scoreDelta,
                safeWinnerDelta,
                safeLoserDelta,
                resolveViewerScoreDelta(viewerUserId, winnerUserId, loserUserId, safeWinnerDelta, safeLoserDelta),
                startedAt,
                endedAt
        );
    }

    private static GobangMoveVO toMoveVO(Long userId, Integer rowIndex, Integer colIndex, Integer chess) {
        return new GobangMoveVO(
                userId,
                rowIndex,
                colIndex,
                chess,
                null,
                null,
                null,
                null
        );
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static Integer resolveViewerScoreDelta(
            Long viewerUserId,
            Long winnerUserId,
            Long loserUserId,
            Integer winnerScoreDelta,
            Integer loserScoreDelta
    ) {
        if (viewerUserId == null) {
            return null;
        }
        if (winnerUserId != null && viewerUserId.equals(winnerUserId)) {
            return value(winnerScoreDelta);
        }
        if (loserUserId != null && viewerUserId.equals(loserUserId)) {
            return value(loserScoreDelta);
        }
        return 0;
    }
}
