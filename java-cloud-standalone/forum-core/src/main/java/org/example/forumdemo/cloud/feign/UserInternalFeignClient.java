package org.example.forumdemo.cloud.feign;

import org.example.forumdemo.entity.db.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 用户域内部接口（返回实体仅供服务间调用；密码等字段已 JsonIgnore）
@FeignClient(name = "forum-auth", contextId = "userInternalFeignClient")
public interface UserInternalFeignClient {

    @GetMapping("/user/internal/{userId}/exists")
    Boolean existsById(@PathVariable("userId") Long userId);

    @GetMapping("/user/internal/{userId}")
    User getById(@PathVariable("userId") Long userId);

    @GetMapping("/user/internal/by-username")
    User getByUsername(@RequestParam("username") String username);

    @PostMapping("/user/internal/{userId}/article-count/increment")
    void incrementArticleCount(@PathVariable("userId") Long userId);

    @PostMapping("/user/internal/{userId}/article-count/decrement")
    void decrementArticleCount(@PathVariable("userId") Long userId);
}
