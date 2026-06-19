package org.example.forumdemo.service.interfaces.mascot;

import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.mascot.MascotChatRequest;
import org.example.forumdemo.entity.vo.mascot.MascotModelPublicVO;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface MascotService {

    /**
     * 转发 Python 看板娘接口；普通用户受每日次数限制.
     */
    Map<String, Object> chat(User user, MascotChatRequest request);

    /** 流式对话（SSE），积分在流结束后结算 */
    void streamChat(User user, MascotChatRequest request, SseEmitter emitter);

    List<MascotModelPublicVO> listPublicModels();
}
