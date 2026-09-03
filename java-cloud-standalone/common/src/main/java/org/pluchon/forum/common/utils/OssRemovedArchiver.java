package org.pluchon.forum.common.utils;

import com.aliyun.oss.OSS;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.OssPaths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 判违规下架：把对象搬进 _removed/，播放立刻 404
@Slf4j
@Component
public class OssRemovedArchiver {

    @Autowired
    private OSS ossClient;

    @Autowired
    private OssConfig ossConfig;

    /**
     * 把正式目录里的对象搬进 `_removed/`。
     *
     * <p>不直接删的原因有两个：留一个可逆窗口（误判能捞回来），以及开了版本控制之后
     * 删除只会加删除标记、空间根本不释放。搬进 `_removed/` 之后由那条前缀的生命周期
     * 规则按天数真删，和 `_pending/` 走的是同一套办法。
     *
     * <p>失败只记日志不抛：下架这件事本身已经由数据库状态生效了，
     * 搬不动文件不该把整个处置流程回滚掉。
     */
    public void archive(String url, String businessPath) {
        if (url == null || url.isBlank()) {
            return;
        }
        String objectKey;
        try {
            objectKey = ossConfig.objectKeyFromPublicUrl(url.trim());
        } catch (IllegalArgumentException ignored) {
            // 不是本站 OSS 的地址，没什么可搬的
            return;
        }
        String businessPrefix = ossConfig.objectKeyPrefix(businessPath);
        if (!objectKey.startsWith(businessPrefix)) {
            // 已经在 _removed/ 里，或者压根不属于这个业务目录
            return;
        }
        String fileName = objectKey.substring(businessPrefix.length());
        if (fileName.isBlank() || fileName.contains("/")) {
            return;
        }
        String removedKey = ossConfig.objectKey(OssPaths.removedFolder(businessPath), fileName);
        String bucket = ossConfig.getBucketName();
        try {
            ossClient.copyObject(bucket, objectKey, bucket, removedKey);
        } catch (Exception exception) {
            log.warn("OSS 违规下架搬迁失败 key={}: {}", objectKey, exception.getMessage());
            return;
        }
        try {
            ossClient.deleteObject(bucket, objectKey);
        } catch (Exception exception) {
            // 搬过去了但原件没删掉，播放还通着。必须报出来，否则违规内容还在外面
            log.error("OSS 违规下架已复制但删除原件失败，内容仍可访问 key={}", objectKey, exception);
            return;
        }
        log.info("OSS 违规下架 {} -> {}", objectKey, removedKey);
    }
}
