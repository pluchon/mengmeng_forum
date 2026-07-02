package org.example.forumdemo.service.interfaces.voice;

import org.example.forumdemo.entity.vo.voice.VoiceIceConfigVO;

// 语音 ICE 配置服务
public interface VoiceIceConfigService {

    VoiceIceConfigVO queryIceConfig(Long loginUserId);
}
