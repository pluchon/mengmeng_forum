package org.pluchon.forum.common.config;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.constant.OssPaths;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssConfig {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String urlPrefix;
    // 兼容旧版 head 路径配置
    private String path;
    // 业务对象 key 根前缀。留空时与 OSS 控制台根目录下 forum_xxx/ 对齐； 若仍使用 forum_db_item/ 包一层，可设 OSS_LOCAL_ROOT_PREFIX / OSS_SERVER_ROOT_PREFIX
    private String rootPrefix = "";

    public boolean isBucketConfigured() {
        String b = bucketName == null ? "" : bucketName.trim();
        return StringUtils.hasText(b) && !"your-forum-oss-bucket".equalsIgnoreCase(b);
    }

    // 拼接 OSS 对象 key：{rootPrefix/}{relativeFolder}{fileName}
    public String objectKey(String relativeFolder, String fileName) {
        String folder = normalizeFolder(relativeFolder);
        String name = fileName == null ? "" : fileName.trim();
        String root = normalizeRoot(rootPrefix);
        return root + folder + name;
    }

    public String objectKeyPrefix(String relativeFolder) {
        String folder = normalizeFolder(relativeFolder);
        String root = normalizeRoot(rootPrefix);
        return root + folder;
    }

    // 外链前缀 + 对象 key 前缀，用于校验客户端回传的 URL
    public String publicUrlPrefix(String relativeFolder) {
        String prefix = urlPrefix == null ? "" : urlPrefix.trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + objectKeyPrefix(relativeFolder);
    }

    // 兼容当前 root prefix 与历史 forum_db_item/ 根目录下的外链
    public boolean matchesPublicObjectUrl(String url, String relativeFolder) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String normalized = url.trim();
        if (normalized.contains("..") || normalized.contains("\\") || containsControlChar(normalized)) {
            return false;
        }
        String current = publicUrlPrefix(relativeFolder);
        if (normalized.startsWith(current)) {
            return true;
        }
        String prefix = urlPrefix == null ? "" : urlPrefix.trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String legacy = prefix + Constant.OSS_LEGACY_ROOT + normalizeFolder(relativeFolder);
        return normalized.startsWith(legacy);
    }

    // 上传审图：允许正式目录或 _pending 下的本站 OSS URL
    public boolean matchesPublicObjectUrlForAudit(String url, String businessPath) {
        return matchesPublicObjectUrl(url, businessPath)
                || matchesPublicObjectUrl(url, OssPaths.pendingFolder(businessPath));
    }

    // 从对外 URL 解析 OSS 对象 key（不含 urlPrefix）
    public String objectKeyFromPublicUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url empty");
        }
        String normalized = url.trim();
        String prefix = urlPrefix == null ? "" : urlPrefix.trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        if (prefix.isEmpty() || !normalized.startsWith(prefix)) {
            throw new IllegalArgumentException("url not under configured oss urlPrefix");
        }
        return normalized.substring(prefix.length());
    }

    private static boolean containsControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRoot(String root) {
        if (root == null || root.isBlank()) {
            return "";
        }
        String r = root.trim().replace('\\', '/');
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        if (!r.isEmpty() && !r.endsWith("/")) {
            r = r + "/";
        }
        return r;
    }

    private static String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "";
        }
        String f = folder.trim().replace('\\', '/');
        while (f.startsWith("/")) {
            f = f.substring(1);
        }
        if (!f.endsWith("/")) {
            f = f + "/";
        }
        return f;
    }
}
