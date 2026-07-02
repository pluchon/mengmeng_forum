package org.example.forumdemo.entity.vo.voice;

import lombok.Data;

import java.util.List;

// WebRTC ICE 配置响应
@Data
public class VoiceIceConfigVO {

    // ICE 服务列表
    private List<VoiceIceServerVO> iceServers;
}
