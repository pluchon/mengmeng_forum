package org.pluchon.forum.economy.client;

import org.pluchon.forum.api.content.UserEngagementInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "forum-content", contextId = "economyUserEngagementInternalFeignClient")
public interface EconomyUserEngagementInternalFeignClient extends UserEngagementInternalApi {
}
