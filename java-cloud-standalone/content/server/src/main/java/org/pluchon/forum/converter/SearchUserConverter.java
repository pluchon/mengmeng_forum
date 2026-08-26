package org.pluchon.forum.converter;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.search.SearchUserItemVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;

// 搜索用户结果转换器
public class SearchUserConverter {

    private SearchUserConverter() {
    }

    public static SearchUserItemVO toItem(UserInternalVO user, UserFollowStatsVO stats) {
        long followingCount = stats == null || stats.getFollowingCount() == null
                ? 0L : stats.getFollowingCount();
        long followerCount = stats == null || stats.getFollowerCount() == null
                ? 0L : stats.getFollowerCount();
        boolean isFollowing = stats != null && Boolean.TRUE.equals(stats.getIsFollowing());
        return new SearchUserItemVO(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                null,
                null,
                followingCount,
                followerCount,
                isFollowing
        );
    }
}
