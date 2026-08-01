package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.game.GameRoomSnapshotVO;

public interface GameRoomSnapshotService {

    void saveSnapshot(GameRoomSnapshotVO snapshot);

    GameRoomSnapshotVO getSnapshot(String gameCode, String roomId);
}
