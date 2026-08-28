package org.pluchon.forum.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

// 关注域内部契约 纯 API，无 @FeignClient；消费方自行声明 Feign 客户端
public interface FollowInternalApi {

    @GetMapping("/user/internal/follow/batch-stats")
    List<FollowStatsInternalVO> batchStats(
            @RequestParam("userIds") List<Long> userIds,
            @RequestParam(value = "viewerId", required = false) Long viewerId
    );

    @GetMapping("/user/internal/follow/{followerId}/following-ids")
    Set<Long> listFollowingIds(@PathVariable("followerId") Long followerId);

    @GetMapping("/user/internal/follow/{userId}/new-count")
    Long countNewFollowers(
            @PathVariable Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    @GetMapping("/user/internal/follow/{userId}/daily-new-counts")
    List<FollowDailyCountInternalVO> listDailyNewFollowers(
            @PathVariable Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );
}
