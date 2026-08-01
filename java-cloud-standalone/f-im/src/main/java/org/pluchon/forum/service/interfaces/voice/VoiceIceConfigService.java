package org.pluchon.forum.service.interfaces.voice;

import org.pluchon.forum.entity.vo.voice.VoiceIceConfigVO;

// 语音 ICE 配置服务
public interface VoiceIceConfigService {

    VoiceIceConfigVO queryIceConfig(Long loginUserId);
}
