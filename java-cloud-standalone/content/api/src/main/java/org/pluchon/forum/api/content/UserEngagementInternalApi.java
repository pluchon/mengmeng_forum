package org.pluchon.forum.api.content;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 用户互动内部契约 纯 API，无 @FeignClient
public interface UserEngagementInternalApi {

    @GetMapping("/article/internal/user/{userId}/daily-engagement")
    UserDailyEngagementInternalVO getDailyEngagement(@PathVariable("userId") Long userId);
}
