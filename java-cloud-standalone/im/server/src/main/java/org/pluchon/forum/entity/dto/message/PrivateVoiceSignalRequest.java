package org.pluchon.forum.entity.dto.message;

import lombok.Data;

// 私聊语音 WebRTC 信令请求
@Data
public class PrivateVoiceSignalRequest {

    // 对方用户ID
    private Long peerUserId;

    // 房间版本
    private Long roomVersion;

    // 发送方连接ID
    private String senderConnectionId;

    // 接收方用户ID
    private Long targetUserId;

    // 接收方连接ID
    private String targetConnectionId;

    // 信令类型
    private String signalType;

    // 信令内容
    private Object payload;
}
