package org.pluchon.forum.entity.vo.voice;

import lombok.Data;

import java.util.List;

// WebRTC ICE 服务配置
@Data
public class VoiceIceServerVO {

    // STUN/TURN 地址
    private List<String> urls;

    // TURN 用户名
    private String username;

    // TURN 密钥
    private String credential;
}
