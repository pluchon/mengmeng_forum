package org.pluchon.forum.common.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 进程内共享 OSS 客户端，避免每次上传 rebuild/shutdown
@Configuration
@Slf4j
public class OssClientConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssConfig ossConfig) {
        if (!ossConfig.isBucketConfigured()) {
            log.warn("OSS bucket 未配置，仍创建客户端；实际上传会在业务层拒绝");
        }
        OSS client = new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret());
        log.info("OSS 单例客户端已创建 endpoint={}", ossConfig.getEndpoint());
        return client;
    }
}
