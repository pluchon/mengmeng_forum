package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.GameTetrisRecord;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.game.TetrisProfileVO;
import org.example.forumdemo.entity.vo.game.TetrisRecordVO;

// 俄罗斯方块实体转换器
public class TetrisConverter {

    private TetrisConverter() {
    }

    public static TetrisProfileVO toProfileVO(GameUserProfile profile, User user) {
        return new TetrisProfileVO(
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                value(profile.getScore()),
                value(profile.getTotalCount()),
                user == null ? 0 : value(user.getPoints())
        );
    }

    public static TetrisRecordVO toRecordVO(GameTetrisRecord record) {
        return new TetrisRecordVO(
                record.getId(),
                value(record.getScore()),
                value(record.getLevel()),
                value(record.getLinesCleared()),
                record.getDurationMs() == null ? 0L : record.getDurationMs(),
                value(record.getForumPointsAwarded()),
                record.getEndedAt()
        );
    }

    private static int value(Integer n) {
        return n == null ? 0 : n;
    }
}
