package org.pluchon.forum.service.interfaces.message;

import org.pluchon.forum.entity.dto.message.PrivateVoiceSignalRequest;
import org.pluchon.forum.entity.vo.message.PrivateVoiceSessionVO;

// 私聊语音服务
public interface PrivateVoiceService {

    PrivateVoiceSessionVO querySession(Long peerUserId, Long loginUserId);

    PrivateVoiceSessionVO startSession(Long peerUserId, Long loginUserId);

    PrivateVoiceSessionVO acceptSession(Long peerUserId, Long loginUserId);

    PrivateVoiceSessionVO declineSession(Long peerUserId, Long loginUserId);

    PrivateVoiceSessionVO leaveSession(Long peerUserId, Long loginUserId);

    void handleSignal(PrivateVoiceSignalRequest request, Long loginUserId);
}
