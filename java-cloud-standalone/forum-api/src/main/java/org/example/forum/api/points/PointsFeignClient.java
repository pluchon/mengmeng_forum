package org.example.forum.api.points;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 积分域内部接口（写操作收口到 forum-economy）
@FeignClient(name = "forum-economy", contextId = "pointsFeignClient")
public interface PointsFeignClient {

    @GetMapping("/points/internal/{userId}/balance")
    Long getBalance(@PathVariable("userId") Long userId);

    @PostMapping("/points/internal/{userId}/add")
    Boolean addPoints(
            @PathVariable("userId") Long userId,
            @RequestParam("delta") long delta,
            @RequestParam("reason") String reason
    );

    @PostMapping("/points/internal/{userId}/deduct")
    Boolean deductPoints(
            @PathVariable("userId") Long userId,
            @RequestParam("delta") long delta,
            @RequestParam("reason") String reason
    );
}
