package org.example.forumdemo.service.interfaces.mascot;

import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.mascot.MascotChatRequest;
import org.example.forumdemo.entity.dto.mascot.MascotRelatedRecommendationRequest;
import org.example.forumdemo.entity.vo.mascot.MascotChatResponseVO;
import org.example.forumdemo.entity.vo.mascot.MascotModelPublicVO;
import org.example.forumdemo.entity.vo.mascot.MascotRelatedRecommendationVO;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MascotService {

    /**
     * 转发 Python 看板娘接口；普通用户受每日次数限制.
     */
    MascotChatResponseVO chat(User user, MascotChatRequest request);

    /** 流式对话（SSE），积分在流结束后结算 */
    void streamChat(User user, MascotChatRequest request, SseEmitter emitter);

    /** 用户确认后执行相关帖子检索，并保存本次展示结果。 */
    MascotRelatedRecommendationVO recommendRelatedArticles(
            User user, MascotRelatedRecommendationRequest request);

    /** 读取当前用户在指定会话中已保存的相关帖子检索结果。 */
    List<MascotRelatedRecommendationVO> listRelatedRecommendations(User user, Long sessionId);

    List<MascotModelPublicVO> listPublicModels();
}
