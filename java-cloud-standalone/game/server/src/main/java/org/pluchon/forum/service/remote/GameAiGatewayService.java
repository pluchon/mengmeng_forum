package org.pluchon.forum.service.remote;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;
import org.pluchon.forum.game.client.GameAiHubInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 游戏服务访问 AI 域的受保护边界
@Slf4j
@Service
public class GameAiGatewayService {

    private static final String RESOURCE_GOBANG = "game.ai.gobang";

    @Autowired
    private GameAiHubInternalFeignClient gameAiHubInternalFeignClient;

    public AiGobangMoveVO chooseGobangMove(AiGobangMoveRequest request) {
        try (Entry ignored = SphU.entry(RESOURCE_GOBANG)) {
            return gameAiHubInternalFeignClient.chooseGobangMove(request);
        } catch (BlockException exception) {
            log.warn("五子棋远程 AI 已限流或熔断，使用本地引擎");
            return null;
        } catch (RuntimeException exception) {
            Tracer.trace(exception);
            log.warn("五子棋远程 AI 不可用，使用本地引擎 error={}", exception.getClass().getSimpleName());
            return null;
        }
    }
}
