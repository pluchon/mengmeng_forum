package org.pluchon.forum.converter;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// 内容域用户简要视图转换
public final class ContentUserBriefConverter {

    private ContentUserBriefConverter() {
    }

    public static UserBriefVO toBrief(UserInternalVO user) {
        return UserBriefVO.from(user);
    }
}
