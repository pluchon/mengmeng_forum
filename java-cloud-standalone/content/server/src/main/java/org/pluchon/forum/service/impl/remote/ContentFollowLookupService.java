package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.FollowStatsInternalVO;
import org.pluchon.forum.api.FollowDailyCountInternalVO;
import org.pluchon.forum.cloud.feign.ContentFollowInternalFeignClient;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;

// 内容域使用的关注只读查询
@Service
public class ContentFollowLookupService {

    @Autowired
    private ContentFollowInternalFeignClient contentFollowInternalFeignClient;

    public Map<Long, UserFollowStatsVO> getBatchStats(Collection<Long> userIds, Long viewerId) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<FollowStatsInternalVO> rows = contentFollowInternalFeignClient.batchStats(List.copyOf(userIds), viewerId);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserFollowStatsVO> result = new HashMap<>(rows.size() * 2);
        for (FollowStatsInternalVO row : rows) {
            if (row == null || row.getUserId() == null) {
                continue;
            }
            result.put(row.getUserId(), new UserFollowStatsVO(
                    row.getUserId(), row.getFollowingCount(), row.getFollowerCount(), row.getIsFollowing()));
        }
        return result;
    }

    public Set<Long> listFollowingIds(Long followerId) {
        Set<Long> ids = contentFollowInternalFeignClient.listFollowingIds(followerId);
        return ids == null ? Set.of() : ids;
    }

    public long countNewFollowers(Long userId, LocalDate startDate, LocalDate endDate) {
        Long count = contentFollowInternalFeignClient.countNewFollowers(
                userId, startDate.toString(), endDate.toString());
        return count == null ? 0L : Math.max(0L, count);
    }

    public List<FollowDailyCountInternalVO> listDailyNewFollowers(
            Long userId, LocalDate startDate, LocalDate endDate) {
        List<FollowDailyCountInternalVO> rows = contentFollowInternalFeignClient.listDailyNewFollowers(
                userId, startDate.toString(), endDate.toString());
        return rows == null ? List.of() : rows;
    }
}
