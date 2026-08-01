package org.pluchon.forum.service.remote;

import org.pluchon.forum.economy.client.EconomyAiHubInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 经济服务访问 AI 网关的本地适配
@Service
public class EconomyAiGatewayService {

    @Autowired
    private EconomyAiHubInternalFeignClient economyAiHubInternalFeignClient;

    public String validateText(String content) {
        return economyAiHubInternalFeignClient.validateText(content);
    }
}
