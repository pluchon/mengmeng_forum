package org.pluchon.forum.service.interfaces.game;

public interface GameRoomEventBusService {

    void publishRoomEvent(String roomId, String payload);

    void publishRoomUserEvent(String roomId, Long userId, String payload);

    void publishGameEvent(String gameCode, Long userId, String payload);

    void publishRoomCommand(String gameCode, String roomId, Long userId, String commandType, String requestId, String data);
}
