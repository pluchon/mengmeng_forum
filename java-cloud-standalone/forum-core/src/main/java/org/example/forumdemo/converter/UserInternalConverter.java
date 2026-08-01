package org.example.forumdemo.converter;

import org.example.forum.api.auth.UserInternalVO;
import org.example.forumdemo.entity.db.User;

// User ↔ UserInternalVO（跨服务契约，不含敏感字段）
public final class UserInternalConverter {

    private UserInternalConverter() {
    }

    public static UserInternalVO toInternalVO(User user) {
        if (user == null) {
            return null;
        }
        UserInternalVO vo = new UserInternalVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBackgroundUrl(user.getBackgroundUrl());
        vo.setArticleCount(user.getArticleCount());
        vo.setIsAdmin(user.getIsAdmin());
        vo.setCreatorState(user.getCreatorState());
        vo.setState(user.getState());
        vo.setGender(user.getGender());
        vo.setRemark(user.getRemark());
        vo.setIpRegion(user.getIpRegion());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    // 仅用于过渡期：远程 VO 填入本地 User 壳，供仍依赖 User 的调用方
    public static User toUserShell(UserInternalVO vo) {
        if (vo == null) {
            return null;
        }
        User user = new User();
        user.setId(vo.getId());
        user.setUsername(vo.getUsername());
        user.setNickname(vo.getNickname());
        user.setAvatarUrl(vo.getAvatarUrl());
        user.setBackgroundUrl(vo.getBackgroundUrl());
        user.setArticleCount(vo.getArticleCount());
        user.setIsAdmin(vo.getIsAdmin());
        user.setCreatorState(vo.getCreatorState());
        user.setState(vo.getState());
        user.setGender(vo.getGender());
        user.setRemark(vo.getRemark());
        user.setIpRegion(vo.getIpRegion());
        user.setCreateTime(vo.getCreateTime());
        return user;
    }
}
