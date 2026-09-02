package org.pluchon.forum.service.interfaces.mascot;

import org.pluchon.forum.entity.dto.MascotIntentCreateRequest;
import org.pluchon.forum.entity.vo.MascotIntentVO;

import java.util.List;

/**
 * 看板娘牵线的意愿池。
 *
 * <p>第一期只做「识别 → 用户确认 → 入池」，不含匹配。人少的时候匹配几乎不会发生，
 * 但意愿可以先攒着；等池子有了真实样本，匹配规则才有依据可定。
 */
public interface MascotIntentService {

    /** 用户在确认卡片上点头后落一条意愿。 */
    MascotIntentVO create(Long userId, MascotIntentCreateRequest request);

    /** 我的意愿列表（只含未过期未取消的）。 */
    List<MascotIntentVO> listMine(Long userId);

    /** 撤掉一条。 */
    void cancel(Long userId, Long intentId);

    /** 「别再帮我留意了」——一次清空。 */
    int cancelAll(Long userId);

    /**
     * 这一轮要不要让看板娘开口问「要不要我留意一下」。
     *
     * <p>克制程度决定这个功能的生死：动不动就问，三天就被用户关掉。所以这里是
     * 代码级的闸，不靠模型自觉——同一会话问过一次就不再问，攒够上限也不再问。
     */
    boolean shouldProbeIntent(Long userId, Long sessionId);
}
