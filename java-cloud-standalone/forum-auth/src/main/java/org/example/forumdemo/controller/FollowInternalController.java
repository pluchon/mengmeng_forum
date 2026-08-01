package org.example.forumdemo.controller;

import org.example.forum.api.auth.FollowStatsInternalVO;
import org.example.forumdemo.entity.vo.user.UserFollowStatsVO;
import org.example.forumdemo.service.interfaces.user.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 关注域内部接口：供 content 等域 Feign 调用
@RestController
@RequestMapping("/user/internal/follow")
public class FollowInternalController {

    @Autowired
    private UserFollowService userFollowService;

    @GetMapping("/batch-stats")
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

    @GetMapping("/{followerId}/following-ids")
    public Set<Long> listFollowingIds(@PathVariable("followerId") Long followerId) {
        return userFollowService.listFollowingIds(followerId);
    }
}
