package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.GroupChat;
import org.example.forumdemo.entity.db.GroupChatMember;
import org.example.forumdemo.entity.db.GroupChatMessage;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.groupchat.GroupChatDetailVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMemberVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMessageVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

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
        vo.setUser(user == null ? null : new UserBriefVO(user));
        vo.setRole(member.getRole());
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
        vo.setSender(sender == null ? null : new UserBriefVO(sender));
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setIsOwner(Objects.equals(message.getSenderUserId(), loginUserId));
        vo.setCreateTime(message.getCreateTime());
        vo.setUpdateTime(message.getUpdateTime());
        return vo;
    }
}
