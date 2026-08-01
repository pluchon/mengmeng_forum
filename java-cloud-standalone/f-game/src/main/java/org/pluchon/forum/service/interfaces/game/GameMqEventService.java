package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.vo.mq.GameFinishedMqVO;

public interface GameMqEventService {

    void handleGameFinished(GameFinishedMqVO event);
}
