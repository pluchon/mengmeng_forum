package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.bo.game.GameMatchBucket;
import org.pluchon.forum.entity.bo.game.GameMatchPair;

public interface GameMatchQueueService {

    boolean enqueue(String gameCode, Long userId, GameMatchBucket bucket);

    boolean dequeue(String gameCode, Long userId);

    boolean contains(String gameCode, Long userId);

    GameMatchPair pollPair(String gameCode, String bucketCode);

    Long pollAiCandidate(String gameCode, String bucketCode, long waitTimeoutMs);
}
