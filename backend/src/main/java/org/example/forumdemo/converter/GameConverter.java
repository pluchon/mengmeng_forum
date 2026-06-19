package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.GameDefinition;
import org.example.forumdemo.entity.db.GameMatchRecord;
import org.example.forumdemo.entity.db.GameRoomMove;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.game.GameDefinitionVO;
import org.example.forumdemo.entity.vo.game.GameMatchRecordVO;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.entity.vo.game.GobangMoveVO;

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
        return new GameUserProfileVO(
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                profile.getGameCode(),
                user == null ? value(profile.getScore()) : value(user.getPoints()),
                total,
                wins,
                value(profile.getLoseCount()),
                value(profile.getDrawCount()),
                winRate,
                profile.getCurrentStatus(),
                profile.getCurrentRoomId()
        );
    }

    public static GameMatchRecordVO toRecordVO(GameMatchRecord row) {
        return new GameMatchRecordVO(
                row.getId(),
                row.getGameCode(),
                row.getRoomId(),
                row.getBlackUserId(),
                row.getWhiteUserId(),
                row.getWinnerUserId(),
                row.getLoserUserId(),
                row.getEndReason(),
                row.getScoreDelta(),
                row.getStartedAt(),
                row.getEndedAt()
        );
    }

    public static GobangMoveVO toMoveVO(GameRoomMove row) {
        return new GobangMoveVO(
                row.getUserId(),
                row.getRowIndex(),
                row.getColIndex(),
                row.getChess(),
                null,
                null,
                null,
                null
        );
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }
}
