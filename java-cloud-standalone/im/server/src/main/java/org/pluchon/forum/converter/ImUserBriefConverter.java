package org.pluchon.forum.converter;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// IM 域用户简要视图转换
public final class ImUserBriefConverter {

    private ImUserBriefConverter() {
    }

    public static UserBriefVO toBrief(UserInternalVO user) {
        return UserBriefVO.from(user);
    }
}
