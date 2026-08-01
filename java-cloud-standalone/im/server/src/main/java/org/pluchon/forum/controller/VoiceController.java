package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.voice.VoiceIceConfigVO;
import org.pluchon.forum.service.interfaces.voice.VoiceIceConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "音视频配置", description = "WebRTC ICE 配置")
@RestController
@RequestMapping("/voice")
public class VoiceController {

    // 语音 ICE 配置服务
    @Autowired
    private VoiceIceConfigService voiceIceConfigService;

    /** 查询 WebRTC ICE 配置 */
    @GetMapping("/ice-config")
    public Result<VoiceIceConfigVO> queryIceConfig(HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(voiceIceConfigService.queryIceConfig(sessionUser.getId()));
    }
}
