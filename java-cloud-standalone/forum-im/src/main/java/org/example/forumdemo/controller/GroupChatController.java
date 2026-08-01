package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.groupchat.CreateGroupChatRequest;
import org.example.forumdemo.entity.dto.groupchat.GroupInviteMemberRequest;
import org.example.forumdemo.entity.dto.groupchat.GroupMuteMemberRequest;
import org.example.forumdemo.entity.dto.groupchat.ReportGroupChatMessageRequest;
import org.example.forumdemo.entity.dto.groupchat.SendGroupChatMessageRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupChatRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupMemberRemarkRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupMemberRoleRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.groupchat.GroupChatDetailVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatJoinRequestVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMemberVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMessageVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatSessionVO;
import org.example.forumdemo.service.interfaces.groupchat.GroupChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "群聊", description = "群聊、成员与消息管理")
@RestController
@RequestMapping("/group-chat")
public class GroupChatController {

    // 群聊业务服务
    @Autowired
    private GroupChatService groupChatService;

    /** 创建群聊 */
    @PostMapping("/create")
    public Result<GroupChatDetailVO> createGroup(@Valid @RequestBody CreateGroupChatRequest request,
                                                 HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.createGroup(request, sessionUser.getId()));
    }

    /** 修改群资料 */
    @PutMapping("/{groupId}")
    public Result<GroupChatDetailVO> updateGroup(@PathVariable Long groupId,
                                                 @RequestBody UpdateGroupChatRequest request,
                                                 HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.updateGroup(groupId, request, sessionUser.getId()));
    }

    /** 查询我的群聊会话 */
    @GetMapping("/sessions")
    public Result<PageResult<GroupChatSessionVO>> queryMySessions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryMySessions(sessionUser.getId(), pageNum, pageSize));
    }

    /** 查询公开群聊 */
    @GetMapping("/public")
    public Result<PageResult<GroupChatDetailVO>> queryPublicGroups(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryPublicGroups(sessionUser.getId(), pageNum, pageSize));
    }

    /** 查询某个用户创建的公开群聊 */
    @GetMapping("/public/users/{ownerUserId}")
    public Result<PageResult<GroupChatDetailVO>> queryOwnerPublicGroups(
            @PathVariable Long ownerUserId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryPublicGroupsByOwner(sessionUser.getId(), ownerUserId, pageNum, pageSize));
    }

    /** 查询我创建的群聊 */
    @GetMapping("/owned")
    public Result<PageResult<GroupChatDetailVO>> queryOwnedGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryMyOwnedGroups(sessionUser.getId(), keyword, pageNum, pageSize));
    }

    /** 申请加入公开群聊 */
    @PostMapping("/{groupId}/join")
    public Result<GroupChatJoinRequestVO> joinPublicGroup(@PathVariable Long groupId,
                                                          HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.joinPublicGroup(groupId, sessionUser.getId()));
    }

    /** 群主邀请成员 */
    @PostMapping("/{groupId}/invite")
    public Result<GroupChatJoinRequestVO> inviteMember(@PathVariable Long groupId,
                                                       @Valid @RequestBody GroupInviteMemberRequest request,
                                                       HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.inviteMember(groupId, request, sessionUser.getId()));
    }

    /** 查询单条入群请求 */
    @GetMapping("/requests/{requestId}")
    public Result<GroupChatJoinRequestVO> queryJoinRequest(@PathVariable Long requestId,
                                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryJoinRequest(requestId, sessionUser.getId()));
    }

    /** 查询我的群收到的入群申请 */
    @GetMapping("/requests/received")
    public Result<PageResult<GroupChatJoinRequestVO>> queryReceivedJoinRequests(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryReceivedJoinRequests(sessionUser.getId(), pageNum, pageSize));
    }

    /** 标记入群申请已查看 */
    @PutMapping("/requests/received/read")
    public Result<String> markReceivedJoinRequestsRead(HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.markReceivedJoinRequestsRead(sessionUser.getId());
        return Result.success("已查看入群申请");
    }

    /** 批准入群申请 */
    @PutMapping("/requests/{requestId}/approve")
    public Result<GroupChatJoinRequestVO> approveJoinRequest(@PathVariable Long requestId,
                                                             HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.approveJoinRequest(requestId, sessionUser.getId()));
    }

    /** 拒绝入群申请 */
    @PutMapping("/requests/{requestId}/reject")
    public Result<GroupChatJoinRequestVO> rejectJoinRequest(@PathVariable Long requestId,
                                                            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.rejectJoinRequest(requestId, sessionUser.getId()));
    }

    /** 同意入群邀请 */
    @PutMapping("/requests/{requestId}/accept")
    public Result<GroupChatJoinRequestVO> acceptInvitation(@PathVariable Long requestId,
                                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.acceptInvitation(requestId, sessionUser.getId()));
    }

    /** 拒绝入群邀请 */
    @PutMapping("/requests/{requestId}/decline")
    public Result<GroupChatJoinRequestVO> rejectInvitation(@PathVariable Long requestId,
                                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.rejectInvitation(requestId, sessionUser.getId()));
    }

    /** 退出群聊 */
    @PostMapping("/{groupId}/leave")
    public Result<String> leaveGroup(@PathVariable Long groupId, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.leaveGroup(groupId, sessionUser.getId());
        return Result.success("已退出群聊");
    }

    /** 群主移除成员 */
    @DeleteMapping("/{groupId}/members/{targetUserId}")
    public Result<String> removeMember(@PathVariable Long groupId,
                                       @PathVariable Long targetUserId,
                                       HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.removeMember(groupId, targetUserId, sessionUser.getId());
        return Result.success("已移除成员");
    }

    /** 群主禁言成员 */
    @PutMapping("/{groupId}/members/mute")
    public Result<String> muteMember(@PathVariable Long groupId,
                                     @Valid @RequestBody GroupMuteMemberRequest request,
                                     HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.muteMember(groupId, request, sessionUser.getId());
        return Result.success("成员禁言状态已更新");
    }

    /** 群主设置或取消管理员 */
    @PutMapping("/{groupId}/members/role")
    public Result<String> updateMemberRole(@PathVariable Long groupId,
                                           @Valid @RequestBody UpdateGroupMemberRoleRequest request,
                                           HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.updateMemberRole(groupId, request, sessionUser.getId());
        return Result.success("成员角色已更新");
    }

    /** 群主解散群聊 */
    @DeleteMapping("/{groupId}")
    public Result<String> dissolveGroup(@PathVariable Long groupId, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.dissolveGroup(groupId, sessionUser.getId());
        return Result.success("群聊已解散");
    }

    /** 发送群聊消息 */
    @PostMapping("/messages")
    public Result<GroupChatMessageVO> sendMessage(@Valid @RequestBody SendGroupChatMessageRequest request,
                                                  HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.sendMessage(request, sessionUser.getId()));
    }

    /** 查询群聊消息 */
    @GetMapping("/{groupId}/messages")
    public Result<PageResult<GroupChatMessageVO>> queryMessages(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryMessages(groupId, sessionUser.getId(), pageNum, pageSize));
    }

    /** 标记群聊已读 */
    @PutMapping("/{groupId}/read")
    public Result<String> markRead(@PathVariable Long groupId,
                                   @RequestParam(required = false) Long messageId,
                                   HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.markRead(groupId, messageId, sessionUser.getId());
        return Result.success("已标记群聊为已读");
    }

    /** 举报群聊消息 */
    @PostMapping("/{groupId}/messages/report")
    public Result<String> reportMessage(@PathVariable Long groupId,
                                        @Valid @RequestBody ReportGroupChatMessageRequest request,
                                        HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        groupChatService.reportMessage(groupId, request, sessionUser.getId());
        return Result.success("举报已提交");
    }

    /** 查询群成员 */
    @GetMapping("/{groupId}/members")
    public Result<List<GroupChatMemberVO>> queryMembers(@PathVariable Long groupId,
                                                        HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.queryMembers(groupId, sessionUser.getId()));
    }

    /** 修改我的群内备注 */
    @PutMapping("/{groupId}/members/me/remark")
    public Result<GroupChatMemberVO> updateMyRemark(@PathVariable Long groupId,
                                                    @RequestBody UpdateGroupMemberRemarkRequest request,
                                                    HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(groupChatService.updateMyRemark(groupId, request, sessionUser.getId()));
    }
}
