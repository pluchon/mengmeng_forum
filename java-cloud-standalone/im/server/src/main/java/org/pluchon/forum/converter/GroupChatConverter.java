package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.GroupChat;
import org.pluchon.forum.entity.db.GroupChatMember;
import org.pluchon.forum.entity.db.GroupChatMessage;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.groupchat.GroupChatDetailVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatMemberVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatMessageVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Objects;

// 群聊实体转换
public final class GroupChatConverter {

    private GroupChatConverter() {
    }

    public static GroupChatDetailVO toDetailVO(GroupChat group) {
        if (group == null) {
            return null;
        }
        GroupChatDetailVO vo = new GroupChatDetailVO();
        vo.setId(group.getId());
        vo.setOwnerUserId(group.getOwnerUserId());
        vo.setName(group.getName());
        vo.setAvatarUrl(group.getAvatarUrl());
        vo.setIntro(group.getIntro());
        vo.setGroupType(group.getGroupType());
        vo.setMemberLimit(group.getMemberLimit());
        vo.setMemberCount(group.getMemberCount());
        vo.setStatus(group.getStatus());
        vo.setCreateTime(group.getCreateTime());
        vo.setUpdateTime(group.getUpdateTime());
        return vo;
    }

    public static GroupChatMemberVO toMemberVO(GroupChatMember member, User user) {
        if (member == null) {
            return null;
        }
        GroupChatMemberVO vo = new GroupChatMemberVO();
        vo.setId(member.getId());
        vo.setGroupId(member.getGroupId());
        vo.setUser(ImUserBriefConverter.toBrief(user));
        vo.setRole(member.getRole());
        vo.setRemarkName(member.getRemarkName());
        vo.setNotifyMode(member.getNotifyMode());
        vo.setMuteUntil(member.getMuteUntil());
        vo.setLastReadMessageId(member.getLastReadMessageId());
        vo.setJoinTime(member.getJoinTime());
        vo.setStatus(member.getStatus());
        return vo;
    }

    public static GroupChatMessageVO toMessageVO(GroupChatMessage message, User sender, Long loginUserId) {
        if (message == null) {
            return null;
        }
        GroupChatMessageVO vo = new GroupChatMessageVO();
        vo.setId(message.getId());
        vo.setGroupId(message.getGroupId());
        vo.setSender(ImUserBriefConverter.toBrief(sender));
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setReplyMessageId(message.getReplyMessageId());
        vo.setReplySenderName(message.getReplySenderName());
        vo.setReplyContent(message.getReplyContent());
        vo.setStatus(message.getStatus());
        vo.setIsOwner(Objects.equals(message.getSenderUserId(), loginUserId));
        vo.setCreateTime(message.getCreateTime());
        vo.setUpdateTime(message.getUpdateTime());
        return vo;
    }
}
