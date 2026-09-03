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

    // 不能用 try-with-resources：entry 会在 catch 之前 exit，异常记不进这个资源，
    // 异常比例熔断就永远不触发。必须在 exit 之前 traceEntry，exit 放进 finally
    public AiGobangMoveVO chooseGobangMove(AiGobangMoveRequest request) {
        Entry entry = null;
        try {
            entry = SphU.entry(RESOURCE_GOBANG);
            return gameAiHubInternalFeignClient.chooseGobangMove(request);
        } catch (BlockException exception) {
            log.warn("五子棋远程 AI 已限流或熔断，使用本地引擎");
            return null;
        } catch (RuntimeException exception) {
            Tracer.traceEntry(exception, entry);
            log.warn("五子棋远程 AI 不可用，使用本地引擎 error={}", exception.getClass().getSimpleName());
            return null;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
}
