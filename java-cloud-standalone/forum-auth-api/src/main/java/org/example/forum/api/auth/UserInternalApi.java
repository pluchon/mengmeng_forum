package org.example.forum.api.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 用户域内部契约（纯 API，无 @FeignClient；消费方自行声明 Feign 客户端）
public interface UserInternalApi {

    @GetMapping("/user/internal/{userId}/exists")
    Boolean existsById(@PathVariable("userId") Long userId);

    @GetMapping("/user/internal/{userId}")
    UserInternalVO getById(@PathVariable("userId") Long userId);

    @GetMapping("/user/internal/by-username")
    UserInternalVO getByUsername(@RequestParam("username") String username);

    @PostMapping("/user/internal/{userId}/article-count/increment")
    void incrementArticleCount(@PathVariable("userId") Long userId);

    @PostMapping("/user/internal/{userId}/article-count/decrement")
    void decrementArticleCount(@PathVariable("userId") Long userId);
}
