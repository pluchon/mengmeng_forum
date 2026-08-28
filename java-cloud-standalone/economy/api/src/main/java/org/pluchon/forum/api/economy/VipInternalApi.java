package org.pluchon.forum.api.economy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

// VIP 域内部契约 纯 API，无 @FeignClient；收口到 forum economy
public interface VipInternalApi {

    @GetMapping("/vip/internal/{userId}/tier")
    VipTierSnapshotVO tierSnapshot(@PathVariable("userId") Long userId);

    @GetMapping("/vip/internal/{userId}/quota-hint")
    VipQuotaHintVO quotaHintForLlmRoute(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "llmRoute", required = false) String llmRoute
    );

}
