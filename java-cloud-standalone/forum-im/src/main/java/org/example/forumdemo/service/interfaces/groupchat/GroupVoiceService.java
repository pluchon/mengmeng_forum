package org.example.forumdemo.service.interfaces.groupchat;

import org.example.forumdemo.entity.dto.groupchat.GroupVoiceSignalRequest;
import org.example.forumdemo.entity.vo.groupchat.GroupVoiceSessionVO;

// 群语音业务接口
public interface GroupVoiceService {

    GroupVoiceSessionVO querySession(Long groupId, Long loginUserId);

    GroupVoiceSessionVO startSession(Long groupId, Long loginUserId);

    GroupVoiceSessionVO joinSession(Long groupId, Long loginUserId);

    GroupVoiceSessionVO leaveSession(Long groupId, Long loginUserId);

    void handleSignal(GroupVoiceSignalRequest request, Long loginUserId);
}
