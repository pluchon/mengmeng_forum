package org.pluchon.forum.converter;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.db.User;

// User ↔ UserInternalVO 跨服务契约，不含敏感字段
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

}
