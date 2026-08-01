package org.pluchon.forum.service.remote;

import org.pluchon.forum.im.client.ImAiHubInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 即时通讯服务访问 AI 网关的本地适配
@Service
public class ImAiGatewayService {

    @Autowired
    private ImAiHubInternalFeignClient imAiHubInternalFeignClient;

    public String validateText(String content) {
        return imAiHubInternalFeignClient.validateText(content);
    }
}
