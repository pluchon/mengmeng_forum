package org.example.forumdemo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.message.PrivateVoiceSessionVO;
import org.example.forumdemo.service.interfaces.message.PrivateVoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message/private-voice")
public class PrivateVoiceController {

    // 私聊语音业务服务
    @Autowired
    private PrivateVoiceService privateVoiceService;

    /** 查询私聊语音状态 */
    @GetMapping("/{peerUserId}")
    public Result<PrivateVoiceSessionVO> queryVoiceSession(@PathVariable Long peerUserId,
                                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(privateVoiceService.querySession(peerUserId, sessionUser.getId()));
    }

    /** 发起私聊语音 */
    @PostMapping("/{peerUserId}/start")
    public Result<PrivateVoiceSessionVO> startVoiceSession(@PathVariable Long peerUserId,
                                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(privateVoiceService.startSession(peerUserId, sessionUser.getId()));
    }

    /** 接听私聊语音 */
    @PostMapping("/{peerUserId}/accept")
    public Result<PrivateVoiceSessionVO> acceptVoiceSession(@PathVariable Long peerUserId,
                                                            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(privateVoiceService.acceptSession(peerUserId, sessionUser.getId()));
    }

    /** 拒绝私聊语音 */
    @PostMapping("/{peerUserId}/decline")
    public Result<PrivateVoiceSessionVO> declineVoiceSession(@PathVariable Long peerUserId,
                                                             HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(privateVoiceService.declineSession(peerUserId, sessionUser.getId()));
    }

    /** 离开私聊语音 */
    @PostMapping("/{peerUserId}/leave")
    public Result<PrivateVoiceSessionVO> leaveVoiceSession(@PathVariable Long peerUserId,
                                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(privateVoiceService.leaveSession(peerUserId, sessionUser.getId()));
    }
}
