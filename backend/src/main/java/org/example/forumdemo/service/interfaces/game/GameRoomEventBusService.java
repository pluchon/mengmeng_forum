package org.example.forumdemo.service.interfaces.game;

public interface GameRoomEventBusService {

    void publishRoomEvent(String roomId, String payload);
}
