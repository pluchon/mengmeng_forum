package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.user.UserFollowListItemVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface UserFollowService {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    UserFollowStatsVO getStats(Long userId, Long viewerId);

    Map<Long, UserFollowStatsVO> getBatchStats(Collection<Long> userIds, Long viewerId);

    /** 当前用户关注的全部用户 ID（用于首页热帖榜等客户端标注） */
    Set<Long> listFollowingIds(Long followerId);

    /** 某用户的关注列表（分页；keyword 仅在关注范围内按昵称/用户名/简介模糊匹配） */
    PageResult<UserFollowListItemVO> listFollowingPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize);

    /** 某用户的粉丝列表（分页；keyword 仅在粉丝范围内按昵称/用户名/简介模糊匹配） */
    PageResult<UserFollowListItemVO> listFollowersPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize);
}
