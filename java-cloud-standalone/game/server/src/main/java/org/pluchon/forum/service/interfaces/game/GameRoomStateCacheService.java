package org.pluchon.forum.service.interfaces.game;

public interface GameRoomStateCacheService {

    void saveState(String gameCode, String roomId, Long userId, Object state);

    <T> T getState(String gameCode, String roomId, Long userId, Class<T> stateType);

    // 房间结束后清掉缓存：留着的话拿旧房号还能「进」一个已经散场的房间
    void clearState(String gameCode, String roomId, Long... userIds);
}
