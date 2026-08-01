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
    Integer getBalance(@PathVariable("userId") Long userId);

    @GetMapping("/points/internal/{userId}/idempotency")
    Boolean hasIdempotencyRecord(
            @PathVariable("userId") Long userId,
            @RequestParam("idempotencyKey") String idempotencyKey
    );

    @PostMapping("/points/internal/{userId}/add")
    Integer addPoints(
            @PathVariable("userId") Long userId,
            @RequestParam("amount") int amount,
            @RequestParam("sourceType") byte sourceType,
            @RequestParam(value = "relatedId", required = false) Long relatedId,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey
    );

    @PostMapping("/points/internal/{userId}/deduct")
    Integer deductPoints(
            @PathVariable("userId") Long userId,
            @RequestParam("amount") int amount,
            @RequestParam("sourceType") byte sourceType,
            @RequestParam(value = "relatedId", required = false) Long relatedId,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey
    );
}
