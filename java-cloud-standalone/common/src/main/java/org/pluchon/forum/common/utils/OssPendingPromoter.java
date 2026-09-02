package org.pluchon.forum.common.utils;

import com.aliyun.oss.OSS;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.OssPaths;
import org.pluchon.forum.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 待定对象转正：把 _pending/ 下的对象搬进正式目录
@Slf4j
@Component
public class OssPendingPromoter {

    @Autowired
    private OSS ossClient;

    @Autowired
    private OssConfig ossConfig;

    /**
     * 绑定时调用：待定 URL 搬进正式目录并返回正式 URL；已经是正式 URL 就原样返回。
     *
     * <p>上传只把对象放进 _pending/，没人绑定的会被 OSS 生命周期规则按天数收走。
     * 所以「转正」这一步就是「有人认领了」的唯一标志，必须发生在真正落库之前。
     *
     * <p>调用方拿到返回值后仍要跑原有的正式目录校验——待定 URL 永远不该落库。
     */
    public String promoteIfPending(String url, String businessPath) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        String pendingPrefix = ossConfig.objectKeyPrefix(OssPaths.pendingFolder(businessPath));
        String objectKey;
        try {
            objectKey = ossConfig.objectKeyFromPublicUrl(trimmed);
        } catch (IllegalArgumentException ignored) {
            // 不是本站 OSS 的 URL，交给调用方原有的校验去拒绝
            return trimmed;
        }
        if (!objectKey.startsWith(pendingPrefix)) {
            return trimmed;
        }
        String fileName = objectKey.substring(pendingPrefix.length());
        if (fileName.isBlank() || fileName.contains("/")) {
            throw new ApplicationException("文件路径不合法，请重新上传");
        }
        String finalKey = ossConfig.objectKey(businessPath, fileName);
        String bucket = ossConfig.getBucketName();
        try {
            OssFolderSupport.ensureFolderExists(ossClient, ossConfig, businessPath);
            ossClient.copyObject(bucket, objectKey, bucket, finalKey);
        } catch (Exception exception) {
            // 最常见的原因是待定对象已过期被生命周期规则收走
            log.warn("OSS 转正失败 pendingKey={} finalKey={}: {}", objectKey, finalKey, exception.getMessage());
            throw new ApplicationException("文件已过期或不存在，请重新上传");
        }
        safeDelete(objectKey);
        log.info("OSS 转正 {} -> {}", objectKey, finalKey);
        return publicUrl(finalKey);
    }

    // 源对象删不掉不影响业务：它留在 _pending/ 下，会被生命周期规则收走
    private void safeDelete(String objectKey) {
        try {
            ossClient.deleteObject(ossConfig.getBucketName(), objectKey);
        } catch (Exception exception) {
            log.warn("OSS 清理待定源失败 key={}: {}", objectKey, exception.getMessage());
        }
    }

    private String publicUrl(String objectKey) {
        String prefix = ossConfig.getUrlPrefix() == null ? "" : ossConfig.getUrlPrefix().trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + objectKey;
    }
}
