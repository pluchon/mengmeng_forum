package org.pluchon.forum.api.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 关注统计内部视图（跨服务只读）
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowStatsInternalVO {

    // 用户 ID
    private Long userId;

    // 关注数
    private Long followingCount;

    // 粉丝数
    private Long followerCount;

    // 当前查看者是否已关注该用户；未登录时为 null
    private Boolean isFollowing;
}
