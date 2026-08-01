package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.vo.mq.GameFinishedMqVO;

public interface GameMqEventService {

    void handleGameFinished(GameFinishedMqVO event);
}
