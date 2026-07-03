package org.example.forumdemo.service.interfaces.game;

public interface GameRoomEventBusService {

    void publishRoomEvent(String roomId, String payload);

    void publishGameEvent(String gameCode, Long userId, String payload);
}
