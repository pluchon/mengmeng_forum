package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.groupchat.GroupVoiceSessionVO;
import org.pluchon.forum.service.interfaces.groupchat.GroupVoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "群语音", description = "群聊语音会话")
@RestController
@RequestMapping("/group-chat")
public class GroupVoiceController {

    // 群语音业务服务
    @Autowired
    private GroupVoiceService groupVoiceService;

    /** 查询群语音状态 */
    @GetMapping("/{groupId}/voice")
    public Result<GroupVoiceSessionVO> queryVoiceSession(@PathVariable Long groupId,
                                                         HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupVoiceService.querySession(groupId, sessionUser.getId()));
    }

    /** 发起群语音 */
    @PostMapping("/{groupId}/voice/start")
    public Result<GroupVoiceSessionVO> startVoiceSession(@PathVariable Long groupId,
                                                         HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupVoiceService.startSession(groupId, sessionUser.getId()));
    }

    /** 加入群语音 */
    @PostMapping("/{groupId}/voice/join")
    public Result<GroupVoiceSessionVO> joinVoiceSession(@PathVariable Long groupId,
                                                        HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupVoiceService.joinSession(groupId, sessionUser.getId()));
    }

    /** 离开群语音 */
    @PostMapping("/{groupId}/voice/leave")
    public Result<GroupVoiceSessionVO> leaveVoiceSession(@PathVariable Long groupId,
                                                         HttpServletRequest httpServletRequest) {
        AuthenticatedUser sessionUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupVoiceService.leaveSession(groupId, sessionUser.getId()));
    }
}
