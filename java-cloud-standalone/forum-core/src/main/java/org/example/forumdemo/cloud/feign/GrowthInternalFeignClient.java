package org.example.forumdemo.cloud.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// 成长域内部接口（收口到 forum-economy）
@FeignClient(name = "forum-economy", contextId = "growthInternalFeignClient")
public interface GrowthInternalFeignClient {

    @PostMapping("/growth/internal/{userId}/require-formal")
    void requireFormalUser(@PathVariable("userId") Long userId);

    @PostMapping("/growth/internal/{userId}/create-profile")
    void createNewUserProfile(@PathVariable("userId") Long userId);
}
