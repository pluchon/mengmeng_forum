package org.pluchon.forum.entity.vo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserFollowListItemVO {
    private UserBriefVO user;
    /** 关注关系建立时间（关注列表=我关注对方的时间；粉丝列表=对方关注我的时间） */
    private Date followTime;
    /** 当前登录用户是否关注了列表中的该用户 */
    private Boolean isFollowing;
    /** 列表中的该用户是否关注了主页用户（粉丝列表恒为 true） */
    private Boolean followsProfileUser;
}
