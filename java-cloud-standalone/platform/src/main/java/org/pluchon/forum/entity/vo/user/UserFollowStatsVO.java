package org.pluchon.forum.entity.vo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserFollowStatsVO {
    private Long userId;
    private Long followingCount;
    private Long followerCount;
    /** 当前登录用户是否已关注该用户；未登录或未传登录态时为 null */
    private Boolean isFollowing;
}
