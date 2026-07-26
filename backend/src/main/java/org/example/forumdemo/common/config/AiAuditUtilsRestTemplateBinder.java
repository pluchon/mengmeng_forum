package org.example.forumdemo.common.config;

import org.example.forumdemo.common.utils.AiAuditUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

// 启动时将 Spring 托管的 RestTemplate 注入 AiAuditUtils，统一超时配置
@Component
public class AiAuditUtilsRestTemplateBinder {

    @Autowired
    public void bind(RestTemplate forumRestTemplate,
                     @Value("${forum.ai.internal-key:}") String internalKey) {
        AiAuditUtils.setRestTemplate(forumRestTemplate);
        AiAuditUtils.setInternalKey(internalKey);
    }
}
