package org.pluchon.forum.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 大厅侧的房间变更广播。
 *
 * <p>观战列表原来靠前端每 5 秒轮询，不管有没有变化都要发一轮请求。房间的开始与结束
 * 都是服务端明确知道的时刻，直接推一条给大厅即可，前端收到再按需拉当前页。
 *
 * <p>只推「有变化」这个信号而不推房间数据本身：观战列表是分页的，推全量会和分页打架，
 * 而且不同客户端停在不同页上。
 */
@Slf4j
@Service
public class GameLobbyBroadcaster {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    public void roomsChanged(String gameCode) {
        if (gameCode == null || gameCode.isBlank()) {
            return;
        }
        try {
            String payload = MAPPER.writeValueAsString(
                    GameWsResponse.ok("active_rooms_changed", null, Map.of("gameCode", gameCode)));
            gameConnectionRegistry.broadcastLobby(payload);
        } catch (Exception e) {
            // 广播只是让列表早点刷新，失败不该影响开局与结算
            log.debug("广播活跃房间变更失败 gameCode={}, error={}", gameCode, e.getMessage());
        }
    }
}
