package org.pluchon.forum.service.interfaces.user;

import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.user.UserFollowListItemVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.util.List;
import org.pluchon.forum.api.FollowDailyCountInternalVO;

public interface UserFollowService {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    UserFollowStatsVO getStats(Long userId, Long viewerId);

    // 当前自然月新增粉丝数 以 Asia/Taipei 的月初为统计起点
    long getCurrentMonthNewFollowerCount(Long userId);

    long countNewFollowers(Long userId, LocalDate startDate, LocalDate endDate);

    List<FollowDailyCountInternalVO> listDailyNewFollowers(Long userId, LocalDate startDate, LocalDate endDate);

    Map<Long, UserFollowStatsVO> getBatchStats(Collection<Long> userIds, Long viewerId);

    // 当前用户关注的全部用户 ID 用于首页热帖榜等客户端标注
    Set<Long> listFollowingIds(Long followerId);

    // 某用户的关注列表 分页；keyword 仅按昵称模糊匹配
    PageResult<UserFollowListItemVO> listFollowingPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize);

    // 某用户的粉丝列表 分页；keyword 仅按昵称模糊匹配
    PageResult<UserFollowListItemVO> listFollowersPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize);
}
