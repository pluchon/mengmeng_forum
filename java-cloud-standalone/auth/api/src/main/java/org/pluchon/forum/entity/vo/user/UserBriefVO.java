package org.pluchon.forum.entity.vo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.api.auth.UserInternalVO;

import java.util.Date;

// 用户简洁信息的返回
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBriefVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private Byte isAdmin;
    private String remark;
    private String backgroundUrl;
    private Byte vipTier;
    private Date vipExpireAt;
    private String ipRegion;

    public static UserBriefVO from(UserInternalVO user) {
        if (user == null) {
            return null;
        }
        UserBriefVO brief = new UserBriefVO();
        brief.id = user.getId();
        brief.nickname = user.getNickname();
        brief.avatarUrl = user.getAvatarUrl();
        brief.isAdmin = user.getIsAdmin();
        brief.remark = user.getRemark();
        brief.backgroundUrl = user.getBackgroundUrl();
        brief.ipRegion = user.getIpRegion();
        return brief;
    }
}
