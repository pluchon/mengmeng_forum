package org.example.forumdemo.entity.dto.groupchat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

// 群语音 WebRTC 信令请求
@Data
public class GroupVoiceSignalRequest {

    // 群聊 ID
    private Long groupId;

    // 房间版本，用于过滤旧房间信令
    private Long roomVersion;

    // 发送方本次加入语音的连接 ID
    private String senderConnectionId;

    // 目标用户 ID
    private Long targetUserId;

    // 目标方本次加入语音的连接 ID
    private String targetConnectionId;

    // 信令类型: offer answer candidate
    private String signalType;

    // WebRTC 信令内容
    private JsonNode payload;
}
