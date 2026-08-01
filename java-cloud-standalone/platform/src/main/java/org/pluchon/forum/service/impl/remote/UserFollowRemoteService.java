package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.auth.FollowStatsInternalVO;
import org.pluchon.forum.cloud.feign.FollowInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.user.UserFollowListItemVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;
import org.pluchon.forum.service.interfaces.user.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 非 auth 域经 Feign 调用关注读接口（写操作走 auth 公开 API）
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'auth'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class UserFollowRemoteService implements UserFollowService {

    @Autowired
    private FollowInternalFeignClient followInternalFeignClient;

    @Override
    public void follow(Long followerId, Long followeeId) {
        throw unsupported("follow");
    }

    @Override
    public void unfollow(Long followerId, Long followeeId) {
        throw unsupported("unfollow");
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        throw unsupported("isFollowing");
    }

    @Override
    public UserFollowStatsVO getStats(Long userId, Long viewerId) {
        Map<Long, UserFollowStatsVO> batch = getBatchStats(List.of(userId), viewerId);
        UserFollowStatsVO stats = batch.get(userId);
        if (stats == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return stats;
    }

    @Override
    public Map<Long, UserFollowStatsVO> getBatchStats(Collection<Long> userIds, Long viewerId) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<FollowStatsInternalVO> rows = followInternalFeignClient.batchStats(List.copyOf(userIds), viewerId);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserFollowStatsVO> result = new HashMap<>(rows.size() * 2);
        for (FollowStatsInternalVO row : rows) {
            if (row == null || row.getUserId() == null) {
                continue;
            }
            result.put(row.getUserId(), new UserFollowStatsVO(
                    row.getUserId(),
                    row.getFollowingCount(),
                    row.getFollowerCount(),
                    row.getIsFollowing()
            ));
        }
        return result;
    }

    @Override
    public Set<Long> listFollowingIds(Long followerId) {
        Set<Long> ids = followInternalFeignClient.listFollowingIds(followerId);
        return ids == null ? Set.of() : ids;
    }

    @Override
    public PageResult<UserFollowListItemVO> listFollowingPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize) {
        throw unsupported("listFollowingPage");
    }

    @Override
    public PageResult<UserFollowListItemVO> listFollowersPage(Long profileUserId, Long viewerId,
            String keyword, Integer pageNum, Integer pageSize) {
        throw unsupported("listFollowersPage");
    }

    private ApplicationException unsupported(String action) {
        return new ApplicationException(Result.fail(
                ResultCode.ERROR_SERVICES,
                "关注写操作与列表请走 auth 服务: " + action
        ));
    }
}
