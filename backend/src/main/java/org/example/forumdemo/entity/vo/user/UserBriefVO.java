package org.example.forumdemo.entity.vo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.forumdemo.entity.db.User;

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

    public UserBriefVO(User user) {
        this.id = user.getId();
        this.nickname = user.getNickname();
        this.avatarUrl = user.getAvatarUrl();
        this.isAdmin = user.getIsAdmin();
        this.remark = user.getRemark();
        this.backgroundUrl = user.getBackgroundUrl();
        this.vipTier = user.getVipTier();
        this.vipExpireAt = user.getVipExpireAt();
    }
}
