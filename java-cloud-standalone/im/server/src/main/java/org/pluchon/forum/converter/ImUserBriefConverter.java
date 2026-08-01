package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// IM 域用户简要视图转换
public final class ImUserBriefConverter {

    private ImUserBriefConverter() {
    }

    public static UserBriefVO toBrief(User user) {
        if (user == null) {
            return null;
        }
        return new UserBriefVO(
                user.getId(), user.getNickname(), user.getAvatarUrl(), user.getIsAdmin(), user.getRemark(),
                user.getBackgroundUrl(), user.getVipTier(), user.getVipExpireAt(), user.getIpRegion());
    }
}
