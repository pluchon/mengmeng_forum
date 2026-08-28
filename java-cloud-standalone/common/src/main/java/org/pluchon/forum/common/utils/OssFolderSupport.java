package org.pluchon.forum.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.OssPaths;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// OSS 上传或启动时若前缀不存在则创建，进程内缓存避免重复探测
@Slf4j
public final class OssFolderSupport {

    private static final Set<String> ENSURED = ConcurrentHashMap.newKeySet();

    private OssFolderSupport() {
    }

    public static void ensureFolderExists(OSS ossClient, OssConfig ossConfig, String relativeFolder) {
        if (ossClient == null || ossConfig == null || !ossConfig.isBucketConfigured()) {
            return;
        }
        String prefix = ossConfig.objectKeyPrefix(relativeFolder);
        if (!StringUtils.hasText(prefix)) {
            return;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String cacheKey = ossConfig.getBucketName() + "|" + prefix;
        if (ENSURED.contains(cacheKey)) {
            return;
        }
        try {
            if (!ossClient.doesObjectExist(ossConfig.getBucketName(), prefix)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(0);
                ossClient.putObject(
                        ossConfig.getBucketName(),
                        prefix,
                        new ByteArrayInputStream(new byte[0]),
                        metadata
                );
                log.info("OSS 目录已创建 bucket={} prefix={}", ossConfig.getBucketName(), prefix);
            }
            ENSURED.add(cacheKey);
        } catch (Exception e) {
            // 目录占位失败不阻断上传：putObject 本身不依赖目录存在
            log.warn("OSS 目录探测/创建失败 bucket={} prefix={}: {}",
                    ossConfig.getBucketName(), prefix, e.getMessage());
        }
    }

    public static void ensureAllBusinessFolders(OSS ossClient, OssConfig ossConfig) {
        for (String path : OssPaths.allBusinessPaths()) {
            ensureFolderExists(ossClient, ossConfig, path);
        }
    }
}
