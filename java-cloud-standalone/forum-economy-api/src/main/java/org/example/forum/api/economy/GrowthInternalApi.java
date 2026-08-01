package org.example.forum.api.economy;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// 成长域内部契约（纯 API，无 @FeignClient；收口到 forum-economy）
public interface GrowthInternalApi {

    @PostMapping("/growth/internal/{userId}/require-formal")
    void requireFormalUser(@PathVariable("userId") Long userId);

    @PostMapping("/growth/internal/{userId}/create-profile")
    void createNewUserProfile(@PathVariable("userId") Long userId);
}
