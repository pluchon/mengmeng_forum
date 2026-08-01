package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.GameTetrisRecord;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.entity.vo.game.TetrisProfileVO;
import org.pluchon.forum.entity.vo.game.TetrisRecordVO;

// 俄罗斯方块实体转换器
public class TetrisConverter {

    private TetrisConverter() {
    }

    public static TetrisProfileVO toProfileVO(GameUserProfile profile, UserInternalVO user) {
        return new TetrisProfileVO(
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                value(profile.getScore()),
                value(profile.getTotalCount()),
                0
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
