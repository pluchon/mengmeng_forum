package org.pluchon.forum.common.config;

import com.aliyun.oss.OSS;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.utils.OssFolderSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 服务启动时确认 OSS 桶，并补齐业务目录占位 路径不存在则创建
@Component
@Slf4j
public class OssFolderInitializer implements ApplicationRunner {

    private static final String LOCAL_DEV_BUCKET = "item-for-picture-with-zhanglihong-local-develop";

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private OSS ossClient;

    @Override
    public void run(ApplicationArguments args) {
        if (!ossConfig.isBucketConfigured()) {
            log.warn("OSS 未配置 bucket，跳过目录初始化");
            return;
        }
        if (!StringUtils.hasText(ossConfig.getAccessKeyId())
                || !StringUtils.hasText(ossConfig.getAccessKeySecret())) {
            log.warn("OSS AccessKey 未配置，跳过目录初始化 bucket={}", ossConfig.getBucketName());
            return;
        }

        String bucket = ossConfig.getBucketName().trim();
        log.info("OSS 当前桶={} urlPrefix={}", bucket, ossConfig.getUrlPrefix());
        if (LOCAL_DEV_BUCKET.equals(bucket)) {
            log.info("已使用本地开发 OSS 桶");
        }

        try {
            if (!ossClient.doesBucketExist(bucket)) {
                log.error("OSS 桶不存在或不具有访问权限: {}", bucket);
                return;
            }
            OssFolderSupport.ensureAllBusinessFolders(ossClient, ossConfig);
        } catch (Exception e) {
            log.warn("OSS 启动目录初始化失败 bucket={}: {}", bucket, e.getMessage());
        }
    }
}
