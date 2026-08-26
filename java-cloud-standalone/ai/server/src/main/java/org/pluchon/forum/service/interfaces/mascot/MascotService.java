package org.pluchon.forum.service.interfaces.mascot;

import org.pluchon.forum.entity.dto.MascotChatRequest;
import org.pluchon.forum.entity.dto.MascotMemoryEditRequest;
import org.pluchon.forum.entity.dto.MascotRelatedRecommendationRequest;
import org.pluchon.forum.entity.vo.MascotChatResponseVO;
import org.pluchon.forum.entity.vo.MascotMemoryVO;
import org.pluchon.forum.entity.vo.MascotModelPublicVO;
import org.pluchon.forum.entity.vo.mascot.MascotQuotaHintVO;
import org.pluchon.forum.entity.vo.MascotRelatedRecommendationVO;
import org.pluchon.forum.entity.vo.CompanionContextWindowVO;
import org.pluchon.forum.service.security.AiUserContext;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MascotService {

    // 转发 Python 看板娘接口；普通用户受每日次数限制.
    MascotChatResponseVO chat(AiUserContext user, MascotChatRequest request, String clientIp);

    // 流式对话 SSE ，积分在流结束后结算
    void streamChat(AiUserContext user, MascotChatRequest request, String clientIp, SseEmitter emitter);

    CompanionContextWindowVO getContextWindow(AiUserContext user, Long sessionId);

    CompanionContextWindowVO compressContext(AiUserContext user, Long sessionId);

    MascotMemoryVO getMascotMemory(AiUserContext user);

    MascotMemoryVO editMascotMemory(AiUserContext user, MascotMemoryEditRequest request);

    // 用户确认后执行相关帖子检索，并保存本次展示结果
    MascotRelatedRecommendationVO recommendRelatedArticles(
            AiUserContext user, MascotRelatedRecommendationRequest request);

    // 读取当前用户在指定会话中已保存的相关帖子检索结果
    List<MascotRelatedRecommendationVO> listRelatedRecommendations(AiUserContext user, Long sessionId);

    List<MascotModelPublicVO> listPublicModels();

    // 写入用户看板娘偏好 权威表 user_mascot_preference
    void setUserMascotPreference(Long userId, Long mascotModelId);

    // 看板娘「使用萌币」按钮配额提示。 在 AI 域本地计算 用量在本域 ，VIP 档位经 Feign 读 economy，避免 Feign 环
    MascotQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute);
}
