package org.pluchon.forum.controller;

import org.pluchon.forum.api.FollowInternalApi;
import org.pluchon.forum.api.FollowStatsInternalVO;
import org.pluchon.forum.api.FollowDailyCountInternalVO;
import org.pluchon.forum.entity.vo.user.UserFollowStatsVO;
import org.pluchon.forum.service.interfaces.user.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;

// 关注域内部接口：契约路径已是 /user/internal/follow/**，勿再叠加 @RequestMapping
@RestController
public class FollowInternalController implements FollowInternalApi {

    @Autowired
    private UserFollowService userFollowService;

    @Override
    public List<FollowStatsInternalVO> batchStats(
            @RequestParam("userIds") List<Long> userIds,
            @RequestParam(value = "viewerId", required = false) Long viewerId) {
        Map<Long, UserFollowStatsVO> stats = userFollowService.getBatchStats(userIds, viewerId);
        List<FollowStatsInternalVO> out = new ArrayList<>(stats.size());
        for (UserFollowStatsVO row : stats.values()) {
            out.add(new FollowStatsInternalVO(
                    row.getUserId(),
                    row.getFollowingCount(),
                    row.getFollowerCount(),
                    row.getIsFollowing()
            ));
        }
        return out;
    }

    @Override
    public Set<Long> listFollowingIds(@PathVariable("followerId") Long followerId) {
        return userFollowService.listFollowingIds(followerId);
    }

    @Override
    public Long countNewFollowers(
            @PathVariable("userId") Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        return userFollowService.countNewFollowers(
                userId, LocalDate.parse(startDate), LocalDate.parse(endDate));
    }

    @Override
    public List<FollowDailyCountInternalVO> listDailyNewFollowers(
            @PathVariable("userId") Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        return userFollowService.listDailyNewFollowers(
                userId, LocalDate.parse(startDate), LocalDate.parse(endDate));
    }
}
