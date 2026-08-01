package org.example.forumdemo.service.impl.file;

import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.OssConfig;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.LotteryImagePathUtils;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.common.utils.ImageCompressor;
import org.example.forumdemo.common.utils.InMemoryMultipartFile;
import org.example.forumdemo.service.interfaces.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文件上传统一实现
 * 全工程唯一持有 OSS 客户端的地方, 所有业务接口都不应再直接处理 MultipartFile,
 * 而是先调用 /file/uploadXxx 拿到 URL, 再通过业务接口把 URL 写入 DB
 *
 * 上传流水线 (uploadImage):
 *   1) validateImageFile : 基础校验 (非空 / 文件名 / 类型白名单 / 硬上限 / GIF 单独限尺寸)
 *   2) maybeCompress     : > 5MB 的静态图走 thumbnailator 压缩, GIF / 已达标图原样返回
 *   3) AiAuditUtils      : 用压缩后的字节做 AI 图片审核, 减小 AI 服务网络压力
 *   4) putObject         : 写入 OSS 并返回外链 URL
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private OssConfig ossConfig;

    @Value("${forum.ffmpeg.internal-key:}")
    private String ffmpegInternalKey;

    @Autowired
    @Qualifier("aiRestTemplate")
    private RestTemplate aiRestTemplate;

    @Autowired
    @Qualifier("ffmpegRestTemplate")
    private RestTemplate ffmpegRestTemplate;

    @Override
    public String uploadAvatar(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_AVATAR);
    }

    @Override
    public String uploadCoverImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_COVER);
    }

    @Override
    public String uploadBackground(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_BACKGROUND);
    }

    @Override
    public String uploadArticleImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_ARTICLE_IMAGE);
    }

    /** 仅超过 200MB 才走 ffmpeg（119MB 级游戏录像全量重编码在弱 CPU 上可能 30min+） */
    private static final long VIDEO_COMPRESS_THRESHOLD = 200L * 1024 * 1024;
    private static final long VIDEO_HARD_MAX_SIZE = 600L * 1024 * 1024;

    @Override
    public String uploadArticleVideo(MultipartFile file, Long userId) {
        ensureOssReady();
        validateVideoFile(file);
        MultipartFile uploadFile = maybeCompressVideo(file);
        String objectName = ossConfig.objectKey(Constant.OSS_PATH_ARTICLE_VIDEO, buildVideoObjectName(userId));
        OSS ossClient = buildOssClient();
        try (InputStream inputStream = uploadFile.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("video/mp4");
            metadata.setContentLength(uploadFile.getSize());
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 视频上传成功, userId={}, key={}, size={}MB",
                    userId, objectName, uploadFile.getSize() / 1024 / 1024);
            return ossConfig.getUrlPrefix() + objectName;
        } catch (Exception e) {
            log.error("OSS 视频上传失败, userId={}, key={}", userId, objectName, e);
            throw new ApplicationException("视频上传 OSS 失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public String uploadChatImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_CHAT_MESSAGE);
    }

    @Override
    public String uploadChatEmoji(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_CHAT_EMOJI);
    }

    @Override
    public String uploadEmojiShopImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_EMOJI_SHOP);
    }

    private static final Pattern DATA_URL_PATTERN = Pattern.compile(
            "^data:([\\w/+.-]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String uploadAiGeneratedImageFromRemote(Long userId, String sourceUrl, String ossPath, String baseName) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址为空"));
        }
        if (ossPath == null || ossPath.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "OSS 路径为空"));
        }
        if (baseName == null || baseName.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "文件名为空"));
        }
        String trimmed = sourceUrl.trim();
        if (trimmed.length() <= 500 && ossConfig.isBucketConfigured()) {
            String urlPrefix = ossConfig.getUrlPrefix() == null ? "" : ossConfig.getUrlPrefix().trim();
            if (!urlPrefix.isEmpty() && trimmed.startsWith(urlPrefix)) {
                String folder = ossPath.endsWith("/") ? ossPath : ossPath + "/";
                if (trimmed.contains(folder)) {
                    return trimmed;
                }
            }
        }
        ResolvedImage img = trimmed.regionMatches(true, 0, "data:", 0, 5)
                ? parseDataUrl(trimmed)
                : downloadRemoteImage(trimmed);
        String ext = extFromContentType(img.contentType());
        String safeBase = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = safeBase + ext;
        MultipartFile file = new InMemoryMultipartFile("file", filename, img.contentType(), img.bytes());
        return uploadImage(file, userId, ossPath);
    }

    private record ResolvedImage(byte[] bytes, String contentType) {}

    private ResolvedImage parseDataUrl(String dataUrl) {
        Matcher m = DATA_URL_PATTERN.matcher(dataUrl.trim());
        if (!m.matches()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "无效的图片 data URL"));
        }
        String contentType = m.group(1).toLowerCase(Locale.ROOT);
        if (!Constant.IMAGE_SUPPORTED_TYPES.contains(contentType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED));
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2).replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片 Base64 解码失败"));
        }
        if (bytes.length == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片内容为空"));
        }
        return new ResolvedImage(bytes, contentType);
    }

    private ResolvedImage downloadRemoteImage(String url) {
        if (!url.regionMatches(true, 0, "https://", 0, 8) && !url.regionMatches(true, 0, "http://", 0, 7)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅支持 http(s) 或 data 图片地址"));
        }
        try {
            ResponseEntity<byte[]> resp = aiRestTemplate.exchange(URI.create(url), HttpMethod.GET, null, byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new ApplicationException("下载 AI 图片失败: 响应为空");
            }
            String contentType = resp.getHeaders().getContentType() != null
                    ? resp.getHeaders().getContentType().toString()
                    : guessContentTypeFromUrl(url);
            if (contentType != null && contentType.contains(";")) {
                contentType = contentType.substring(0, contentType.indexOf(';')).trim();
            }
            if (contentType == null || !Constant.IMAGE_SUPPORTED_TYPES.contains(contentType)) {
                contentType = guessContentTypeFromUrl(url);
            }
            if (!Constant.IMAGE_SUPPORTED_TYPES.contains(contentType)) {
                contentType = "image/png";
            }
            return new ResolvedImage(bytes, contentType);
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载 AI 生图失败 url={}: {}", url.length() > 120 ? url.substring(0, 120) + "..." : url, e.getMessage());
            throw new ApplicationException("下载 AI 图片失败: " + e.getMessage());
        }
    }

    private static String guessContentTypeFromUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String extFromContentType(String contentType) {
        if (Constant.IMAGE_TYPE_GIF.equalsIgnoreCase(contentType)) {
            return ".gif";
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }
        return ".jpg";
    }

    @Override
    public String uploadLotteryPrizePicture(MultipartFile file, long activityId, long prizeId) {
        ensureOssReady();
        validateImageFile(file);
        MultipartFile uploadFile = maybeCompress(file);
        if (!AiAuditUtils.isImageAllowed(uploadFile)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_VIOLATION));
        }
        String ext = extFromOriginalName(uploadFile.getOriginalFilename());
        String ts = LotteryImagePathUtils.nowTs();
        String objectName = ossConfig.objectKey(
                Constant.OSS_PATH_LOTTERY_PRIZE,
                LotteryImagePathUtils.prizeImageObjectName(activityId, prizeId, ts, ext));
        OSS ossClient = buildOssClient();
        try (InputStream inputStream = uploadFile.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(uploadFile.getContentType());
            metadata.setContentLength(uploadFile.getSize());
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 抽奖奖品图上传成功 activityId={}, prizeId={}, key={}", activityId, prizeId, objectName);
            return ossConfig.getUrlPrefix() + objectName;
        } catch (Exception e) {
            log.error("OSS 抽奖奖品图上传失败 activityId={}, prizeId={}, key={}", activityId, prizeId, objectName, e);
            throw new ApplicationException("文件上传 OSS 失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public String uploadLotteryActivityPicture(MultipartFile file, long activityId, long publisherUserId) {
        ensureOssReady();
        validateImageFile(file);
        MultipartFile uploadFile = maybeCompress(file);
        if (!AiAuditUtils.isImageAllowed(uploadFile)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_VIOLATION));
        }
        String ext = extFromOriginalName(uploadFile.getOriginalFilename());
        String ts = LotteryImagePathUtils.nowTs();
        String objectName = ossConfig.objectKey(
                Constant.OSS_PATH_LOTTERY_ACTIVITY,
                LotteryImagePathUtils.activityCoverObjectName(activityId, publisherUserId, ts, ext));
        OSS ossClient = buildOssClient();
        try (InputStream inputStream = uploadFile.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(uploadFile.getContentType());
            metadata.setContentLength(uploadFile.getSize());
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 抽奖活动封面上传成功 activityId={}, publisherId={}, key={}", activityId, publisherUserId, objectName);
            return ossConfig.getUrlPrefix() + objectName;
        } catch (Exception e) {
            log.error("OSS 抽奖活动封面上传失败 activityId={}, publisherId={}, key={}", activityId, publisherUserId, objectName, e);
            throw new ApplicationException("文件上传 OSS 失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public String uploadNoticePicture(MultipartFile file, Long publisherUserId, Long noticeId) {
        ensureOssReady();
        validateImageFile(file);
        MultipartFile uploadFile = maybeCompress(file);
        if (!AiAuditUtils.isImageAllowed(uploadFile)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_VIOLATION));
        }
        String objectName = ossConfig.objectKey(
                Constant.OSS_PATH_NOTICE_PICTURE,
                buildNoticePictureObjectName(uploadFile, publisherUserId, noticeId != null ? noticeId : 0L));
        OSS ossClient = buildOssClient();
        try (InputStream inputStream = uploadFile.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(uploadFile.getContentType());
            metadata.setContentLength(uploadFile.getSize());
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 公告配图上传成功, publisherId={}, noticeId={}, key={}", publisherUserId, noticeId, objectName);
            return ossConfig.getUrlPrefix() + objectName;
        } catch (Exception e) {
            log.error("OSS 公告配图上传失败, publisherId={}, noticeId={}, key={}", publisherUserId, noticeId, objectName, e);
            throw new ApplicationException("文件上传 OSS 失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    /** 不含点的扩展名，供 lottery 对象名 {@code xxx.yyy} 使用 */
    private static String extFromOriginalName(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "jpg";
        }
        String e = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (e.isEmpty() || e.length() > 8) {
            return "jpg";
        }
        return e;
    }

    /** 发布者ID + 公告ID + 东八区发布时间(到秒) + 扩展名 */
    private String buildNoticePictureObjectName(MultipartFile file, Long publisherUserId, long noticeId) {
        String originalFilename = file.getOriginalFilename();
        String extName = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extName = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String timeStr = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return publisherUserId + "_" + noticeId + "_" + timeStr + extName;
    }

    private void ensureOssReady() {
        if (!ossConfig.isBucketConfigured()) {
            throw new ApplicationException(
                    "OSS 未配置：请设置环境变量 OSS_BUCKET_NAME 与 OSS_URL_PREFIX（勿使用占位符 your-forum-oss-bucket）");
        }
    }

    /** 共用上传逻辑: 参数校验 + (可选)压缩 + AI 审核 + OSS 写入 */
    private String uploadImage(MultipartFile file, Long userId, String pathPrefix) {
        ensureOssReady();
        validateImageFile(file);
        MultipartFile uploadFile = maybeCompress(file);
        if (!AiAuditUtils.isImageAllowed(uploadFile)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_VIOLATION));
        }
        String objectName = ossConfig.objectKey(pathPrefix, buildObjectName(uploadFile, userId));
        OSS ossClient = buildOssClient();
        try (InputStream inputStream = uploadFile.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(uploadFile.getContentType());
            metadata.setContentLength(uploadFile.getSize());
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 上传成功, userId={}, key={}, size={}KB",
                    userId, objectName, uploadFile.getSize() / 1024);
            return ossConfig.getUrlPrefix() + objectName;
        } catch (Exception e) {
            log.error("OSS 上传失败, userId={}, key={}", userId, objectName, e);
            throw new ApplicationException("文件上传 OSS 失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 基础校验:
     * - 非空 / 文件名合法
     * - contentType 必须落在白名单 (JPG / PNG / GIF), WebP / HEIC 等其他格式直接拒
     * - 大小不能超过服务器硬上限 30MB
     * - GIF 单独限制: 不超过 15MB (动图本身体积大, 但不参与压缩)
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "上传文件不能为空"));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "无效的文件名称"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !Constant.IMAGE_SUPPORTED_TYPES.contains(contentType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED));
        }
        if (file.getSize() > Constant.IMAGE_HARD_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "图片不能超过 " + (Constant.IMAGE_HARD_MAX_SIZE / 1024 / 1024) + "MB"));
        }
        if (Constant.IMAGE_TYPE_GIF.equalsIgnoreCase(contentType)
                && file.getSize() > Constant.IMAGE_GIF_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "GIF 动图不能超过 " + (Constant.IMAGE_GIF_MAX_SIZE / 1024 / 1024) + "MB"));
        }
    }

    /**
     * 决定是否进入压缩通道:
     * - GIF : 不压缩 (会丢帧), 直接返回原文件
     * - 静态图 ≤ 5MB : 直接返回原文件
     * - 静态图 > 5MB : 走 thumbnailator 压缩, 包成 InMemoryMultipartFile 返回
     */
    private MultipartFile maybeCompress(MultipartFile file) {
        String contentType = file.getContentType();
        if (Constant.IMAGE_TYPE_GIF.equalsIgnoreCase(contentType)) {
            return file;
        }
        if (file.getSize() <= Constant.OSS_MAX_IMAGE_SIZE) {
            return file;
        }
        byte[] compressed = ImageCompressor.compress(file);
        if (compressed.length > Constant.IMAGE_COMPRESS_MAX_OUTPUT_SIZE) {
            log.warn("压缩后仍超过上限: name={}, after={}KB",
                    file.getOriginalFilename(), compressed.length / 1024);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_COMPRESS));
        }
        // 压缩输出统一为 JPEG, 重写文件名后缀, 确保与 contentType 一致
        String newName = replaceExtension(file.getOriginalFilename(), ".jpg");
        return new InMemoryMultipartFile(file.getName(), newName, "image/jpeg", compressed);
    }

    /** 拼接 OSS 对象名: {userId}_{时间}_{8位UUID}.{ext} */
    private String buildObjectName(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        // 上一步 validate 已保证文件名包含 "."
        String extName = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extName = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = IdUtil.simpleUUID().substring(0, 8);
        return userId + "_" + timeStr + "_" + uuid + extName;
    }

    /** 替换文件名扩展名, 用于压缩后将 png/jpeg 统一改写为 .jpg */
    private String replaceExtension(String originalFilename, String newExt) {
        if (originalFilename == null || originalFilename.isEmpty()) {
            return "image" + newExt;
        }
        int idx = originalFilename.lastIndexOf('.');
        String base = idx > 0 ? originalFilename.substring(0, idx) : originalFilename;
        return base + newExt;
    }

    private OSS buildOssClient() {
        return new OSSClientBuilder().build(ossConfig.getEndpoint(), ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "上传视频不能为空"));
        }
        if (file.getSize() > VIDEO_HARD_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "视频不能超过 " + (VIDEO_HARD_MAX_SIZE / 1024 / 1024) + "MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "无法识别视频类型"));
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("video/") || lower.equals("application/octet-stream"))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅支持视频文件上传"));
        }
    }

    /** 视频对象名: {userId}_{yyyyMMddHHmmss}.mp4 */
    private String buildVideoObjectName(Long userId) {
        String timeStr = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return userId + "_" + timeStr + ".mp4";
    }

    /**
     * >200MB 走 ffmpeg（优先 remux 不重编码），否则原样上传 OSS。
     */
    private MultipartFile maybeCompressVideo(MultipartFile file) {
        if (file.getSize() <= VIDEO_COMPRESS_THRESHOLD) {
            return file;
        }
        long t0 = System.currentTimeMillis();
        long inMb = file.getSize() / 1024 / 1024;
        String baseUrl = System.getenv().getOrDefault("FORUM_FFMPEG_URL", "http://ffmpeg:8099");
        String url = baseUrl.endsWith("/") ? baseUrl + "compress" : baseUrl + "/compress";
        log.info("视频开始压缩 name={} size={}MB ffmpeg={}", file.getOriginalFilename(), inMb, baseUrl);
        try {
            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.InputStreamResource(file.getInputStream()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
                }
                @Override
                public long contentLength() {
                    return file.getSize();
                }
            });
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
            if (ffmpegInternalKey != null && !ffmpegInternalKey.isBlank()) {
                headers.set("X-Internal-Key", ffmpegInternalKey);
            }
            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> req =
                    new org.springframework.http.HttpEntity<>(body, headers);
            ResponseEntity<byte[]> resp = ffmpegRestTemplate.postForEntity(url, req, byte[].class);
            byte[] bytes = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0) {
                throw new ApplicationException("视频压缩失败: 空响应");
            }
            log.info("视频压缩完成 name={} in={}MB out={}MB 耗时={}ms",
                    file.getOriginalFilename(), inMb, bytes.length / 1024 / 1024, System.currentTimeMillis() - t0);
            return new InMemoryMultipartFile("file", replaceExtension(file.getOriginalFilename(), ".mp4"), "video/mp4", bytes);
        } catch (ApplicationException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ApplicationException("视频处理队列繁忙，请稍后再试（不要重复点击上传）");
            }
            log.error("视频压缩服务 HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ApplicationException("视频压缩失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("视频压缩服务调用失败: {}", e.getMessage());
            throw new ApplicationException("视频压缩失败: " + e.getMessage());
        }
    }
}
