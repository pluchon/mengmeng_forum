package org.pluchon.forum.service.interfaces.game;

public interface GameRoomStateCacheService {

    void saveState(String gameCode, String roomId, Long userId, Object state);

    <T> T getState(String gameCode, String roomId, Long userId, Class<T> stateType);
}
