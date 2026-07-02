package org.example.forumdemo.service.interfaces.groupchat;

import org.example.forumdemo.entity.dto.groupchat.CreateGroupChatRequest;
import org.example.forumdemo.entity.dto.groupchat.GroupInviteMemberRequest;
import org.example.forumdemo.entity.dto.groupchat.GroupMuteMemberRequest;
import org.example.forumdemo.entity.dto.groupchat.ReportGroupChatMessageRequest;
import org.example.forumdemo.entity.dto.groupchat.SendGroupChatMessageRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupMemberRoleRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupChatRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupMemberRemarkRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.groupchat.GroupChatDetailVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatJoinRequestVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMemberVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMessageVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatSessionVO;

import java.util.List;

// 群聊业务接口
public interface GroupChatService {

    GroupChatDetailVO createGroup(CreateGroupChatRequest request, Long loginUserId);

    GroupChatDetailVO updateGroup(Long groupId, UpdateGroupChatRequest request, Long loginUserId);

    PageResult<GroupChatSessionVO> queryMySessions(Long loginUserId, Integer pageNum, Integer pageSize);

    PageResult<GroupChatDetailVO> queryPublicGroups(Long loginUserId, Integer pageNum, Integer pageSize);

    PageResult<GroupChatDetailVO> queryPublicGroupsByOwner(Long loginUserId, Long ownerUserId, Integer pageNum, Integer pageSize);

    PageResult<GroupChatDetailVO> queryMyOwnedGroups(Long loginUserId, String keyword, Integer pageNum, Integer pageSize);

    GroupChatJoinRequestVO joinPublicGroup(Long groupId, Long loginUserId);

    GroupChatJoinRequestVO inviteMember(Long groupId, GroupInviteMemberRequest request, Long loginUserId);

    GroupChatJoinRequestVO queryJoinRequest(Long requestId, Long loginUserId);

    PageResult<GroupChatJoinRequestVO> queryReceivedJoinRequests(Long loginUserId, Integer pageNum, Integer pageSize);

    void markReceivedJoinRequestsRead(Long loginUserId);

    GroupChatJoinRequestVO approveJoinRequest(Long requestId, Long loginUserId);

    GroupChatJoinRequestVO rejectJoinRequest(Long requestId, Long loginUserId);

    GroupChatJoinRequestVO acceptInvitation(Long requestId, Long loginUserId);

    GroupChatJoinRequestVO rejectInvitation(Long requestId, Long loginUserId);

    void leaveGroup(Long groupId, Long loginUserId);

    void removeMember(Long groupId, Long targetUserId, Long loginUserId);

    void muteMember(Long groupId, GroupMuteMemberRequest request, Long loginUserId);

    void updateMemberRole(Long groupId, UpdateGroupMemberRoleRequest request, Long loginUserId);

    void dissolveGroup(Long groupId, Long loginUserId);

    GroupChatMessageVO sendMessage(SendGroupChatMessageRequest request, Long loginUserId);

    PageResult<GroupChatMessageVO> queryMessages(Long groupId, Long loginUserId, Integer pageNum, Integer pageSize);

    void markRead(Long groupId, Long messageId, Long loginUserId);

    void reportMessage(Long groupId, ReportGroupChatMessageRequest request, Long loginUserId);

    List<GroupChatMemberVO> queryMembers(Long groupId, Long loginUserId);

    GroupChatMemberVO updateMyRemark(Long groupId, UpdateGroupMemberRemarkRequest request, Long loginUserId);
}
