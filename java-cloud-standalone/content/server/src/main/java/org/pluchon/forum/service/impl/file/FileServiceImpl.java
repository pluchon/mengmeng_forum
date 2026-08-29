package org.pluchon.forum.service.impl.file;

import org.pluchon.forum.common.constant.ForumTimeZone;
import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.RedisWindowCounter;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.OssFolderSupport;
import org.pluchon.forum.common.utils.ImageCompressor;
import org.pluchon.forum.common.utils.ImageMagicValidator;
import org.pluchon.forum.common.utils.InMemoryMultipartFile;
import org.pluchon.forum.entity.vo.file.BatchImageUploadResultVO;
import org.pluchon.forum.service.interfaces.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

// 文件上传与对象存储服务实现
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AuditedOssImageUploader auditedOssImageUploader;

    @Autowired
    @Qualifier("imageAuditExecutor")
    private ExecutorService imageAuditExecutor;

    @Autowired
    private OSS ossClient;

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
    public String uploadFavoriteFolderCover(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_FAVORITE_FOLDER);
    }

    @Override
    public String uploadArticleImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_ARTICLE_IMAGE);
    }

    @Override
    public BatchImageUploadResultVO uploadArticleImages(MultipartFile[] files, Long userId) {
        return uploadImagesBatch(files, userId, Constant.OSS_PATH_ARTICLE_IMAGE);
    }

    // 仅超过 200MB 才走 ffmpeg 119MB 级游戏录像全量重编码在弱 CPU 上可能 30min+
    private static final long VIDEO_COMPRESS_THRESHOLD = 200L * 1024 * 1024;
    // nginx client_max_body_size 与 spring multipart 都是 350MB，
    // 之前这里写 600MB 永远够不着：超过 350MB 会先被 nginx 用 413 掐掉。
    // 留 10MB 给 multipart 边界与表单字段开销
    private static final long VIDEO_HARD_MAX_SIZE = 340L * 1024 * 1024;

    @Override
    public String uploadArticleVideo(MultipartFile file, Long userId) {
        ensureOssReady();
        validateVideoFile(file);
        MultipartFile uploadFile = maybeCompressVideo(file);
        String objectName = ossConfig.objectKey(Constant.OSS_PATH_ARTICLE_VIDEO, buildVideoObjectName(userId));
        OSS ossClient = this.ossClient;
        try (InputStream inputStream = uploadFile.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("video/mp4");
            metadata.setContentLength(uploadFile.getSize());
            OssFolderSupport.ensureFolderExists(ossClient, ossConfig, Constant.OSS_PATH_ARTICLE_VIDEO);
            ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream, metadata);
            log.info("OSS 视频上传成功, userId={}, key={}, size={}MB",
                    userId, objectName, uploadFile.getSize() / 1024 / 1024);
            return ossConfig.getUrlPrefix() + objectName;
        } catch (Exception e) {
            log.error("OSS 视频上传失败, userId={}, key={}", userId, objectName, e);
            throw new ApplicationException("视频上传 OSS 失败: " + e.getMessage());
        } finally {
        }
    }

    @Override
    public String uploadChatImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_CHAT_MESSAGE);
    }

    @Override
    public BatchImageUploadResultVO uploadChatImages(MultipartFile[] files, Long userId) {
        return uploadImagesBatch(files, userId, Constant.OSS_PATH_CHAT_MESSAGE);
    }

    @Override
    public String uploadChatEmoji(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_CHAT_EMOJI);
    }

    @Override
    public BatchImageUploadResultVO uploadChatEmojis(MultipartFile[] files, Long userId) {
        return uploadImagesBatch(files, userId, Constant.OSS_PATH_CHAT_EMOJI);
    }

    @Override
    public String uploadEmojiShopImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, Constant.OSS_PATH_EMOJI_SHOP);
    }

    @Override
    public BatchImageUploadResultVO uploadEmojiShopImages(MultipartFile[] files, Long userId) {
        return uploadImagesBatch(files, userId, Constant.OSS_PATH_EMOJI_SHOP);
    }

    // 批量上传共用：校验 + 压缩落内存 + uploadBatch，最多 9 张，允许部分成功
    private BatchImageUploadResultVO uploadImagesBatch(MultipartFile[] files, Long userId, String pathPrefix) {
        ensureOssReady();
        if (files == null || files.length == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "上传文件不能为空"));
        }
        if (files.length > 9) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "单次最多上传9张图片"));
        }
        assertUploadQuota(userId, files.length);
        List<MultipartFile> prepared = new ArrayList<>(files.length);
        List<String> fileNames = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            validateImageFile(file);
            MultipartFile uploadFile = materializeUploadFile(maybeCompress(file));
            prepared.add(uploadFile);
            fileNames.add(buildObjectName(uploadFile, userId));
        }
        return auditedOssImageUploader.uploadBatch(prepared, pathPrefix, fileNames, imageAuditExecutor);
    }

    // 上传接口不绑帖子，"单次 9 张"和"落库 15 张"都拦不住反复调用，
    // 而每张图都要占 OSS 空间并过一次 AI 审图，两头都是钱。这里按用户做一层窗口计数
    private void assertUploadQuota(Long userId, int count) {
        if (userId == null || count <= 0) {
            return;
        }
        String key = Constant.REDIS_KEY_IMAGE_UPLOAD_COUNT + userId;
        for (int i = 0; i < count; i++) {
            boolean allowed = RedisWindowCounter.tryAcquire(stringRedisTemplate, key,
                    Constant.IMAGE_UPLOAD_USER_MAX_COUNT, Constant.REDIS_TTL_IMAGE_UPLOAD_COUNT);
            if (!allowed) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_RATE_LIMITED,
                        "图片上传太频繁了，请稍后再试"));
            }
        }
    }

    private static final Pattern DATA_URL_PATTERN = Pattern.compile(
            "^data:([\\w/+.-]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String uploadAiGeneratedImageFromRemote(Long userId, String sourceUrl, String ossPath, String baseName) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请先选择图片"));
        }
        if (ossPath == null || ossPath.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无效，请重新上传"));
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
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片格式无法识别，请重新上传"));
        }
        String contentType = m.group(1).toLowerCase(Locale.ROOT);
        if (!Constant.IMAGE_SUPPORTED_TYPES.contains(contentType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED));
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2).replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片读取失败，请重新上传"));
        }
        if (bytes.length == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片内容为空"));
        }
        return new ResolvedImage(bytes, contentType);
    }

    private ResolvedImage downloadRemoteImage(String url) {
        if (!url.regionMatches(true, 0, "https://", 0, 8) && !url.regionMatches(true, 0, "http://", 0, 7)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片地址无效，请重新上传"));
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

    private void ensureOssReady() {
        if (!ossConfig.isBucketConfigured()) {
            throw new ApplicationException(
                    "OSS 未配置：请设置 OSS_LOCAL_BUCKET_NAME 与 OSS_LOCAL_URL_PREFIX（本地）或 OSS_SERVER_*（服务器）");
        }
    }

    // 共用上传逻辑: 参数校验 + 可选压缩 + pending OSS + URL 审图 + promote
    private String uploadImage(MultipartFile file, Long userId, String pathPrefix) {
        ensureOssReady();
        assertUploadQuota(userId, 1);
        validateImageFile(file);
        MultipartFile uploadFile = materializeUploadFile(maybeCompress(file));
        String fileName = buildObjectName(uploadFile, userId);
        return auditedOssImageUploader.upload(uploadFile, pathPrefix, fileName);
    }

    // 基础校验: 非空 / 文件名合法 contentType 必须落在白名单 JPG / PNG / GIF , WebP / HEIC 等其他格式直接拒 大小不能超过服务器硬上限 30MB GIF 单独限制: 不超过 15MB 动图本身体积大, 但不参与压缩
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
        ImageMagicValidator.validateSupportedImage(file);
    }

    // 决定是否进入压缩通道: GIF : 不压缩 会丢帧 , 直接返回原文件 静态图 ≤ 5MB : 直接返回原文件 静态图 > 5MB : 走 thumbnailator 压缩, 包成 InMemoryMultipartFile 返回
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

    // 压缩后或原文件统一落内存，避免流被 Feign / OSS 重复读取时偶发为空
    private MultipartFile materializeUploadFile(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new InMemoryMultipartFile(
                    file.getName(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    bytes);
        } catch (Exception e) {
            log.error("读取上传图片失败: name={}", file.getOriginalFilename(), e);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图片读取失败，请重新上传"));
        }
    }

    // 拼接 OSS 对象名: {userId}_{时间}_{8位UUID}.{ext}
    private String buildObjectName(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        // 上一步 validate 已保证文件名包含 .
        String extName = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extName = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = IdUtil.simpleUUID();
        return userId + "_" + timeStr + "_" + uuid + extName;
    }

    // 替换文件名扩展名, 用于压缩后将 png/jpeg 统一改写为 .jpg
    private String replaceExtension(String originalFilename, String newExt) {
        if (originalFilename == null || originalFilename.isEmpty()) {
            return "image" + newExt;
        }
        int idx = originalFilename.lastIndexOf('.');
        String base = idx > 0 ? originalFilename.substring(0, idx) : originalFilename;
        return base + newExt;
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
        // octet-stream 是给不报 MIME 的浏览器留的口子，等于把白名单架空，
        // 再用扩展名兜一道，避免任意二进制文件被丢给 ffmpeg 去啃
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || !Constant.VIDEO_SUPPORTED_EXTENSIONS.contains(name.substring(dot))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "仅支持 MP4 / MOV / M4V / WEBM 格式的视频"));
        }
    }

    // 视频对象名: {userId}_{yyyyMMddHHmmss}.mp4
    private String buildVideoObjectName(Long userId) {
        String timeStr = ZonedDateTime.now(ForumTimeZone.ZONE_ID)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return userId + "_" + timeStr + ".mp4";
    }

    // >200MB 走 ffmpeg 优先 remux 不重编码 ，否则原样上传 OSS
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

    @Override
    public String transcodeArticleVideoToHls(Long articleId, String sourceVideoUrl) {
        ensureOssReady();
        if (articleId == null || articleId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (sourceVideoUrl == null || sourceVideoUrl.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请先上传视频"));
        }
        if (!ossConfig.matchesPublicObjectUrl(sourceVideoUrl.trim(), Constant.OSS_PATH_ARTICLE_VIDEO)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "视频地址无效，请重新上传"));
        }
        long t0 = System.currentTimeMillis();
        String baseUrl = System.getenv().getOrDefault("FORUM_FFMPEG_URL", "http://ffmpeg:8099");
        String url = baseUrl.endsWith("/") ? baseUrl + "transcode-hls" : baseUrl + "/transcode-hls";
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceUrl", sourceVideoUrl.trim());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ffmpegInternalKey != null && !ffmpegInternalKey.isBlank()) {
            headers.set("X-Internal-Key", ffmpegInternalKey);
        }
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
        ResponseEntity<byte[]> resp;
        try {
            resp = ffmpegRestTemplate.postForEntity(url, req, byte[].class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ApplicationException("视频转码队列繁忙，请稍后再试");
            }
            log.error("HLS 转码服务 HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ApplicationException("视频 HLS 转码失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("HLS 转码服务调用失败: {}", e.getMessage());
            throw new ApplicationException("视频 HLS 转码失败: " + e.getMessage());
        }
        byte[] zipBytes = resp.getBody();
        if (!resp.getStatusCode().is2xxSuccessful() || zipBytes == null || zipBytes.length == 0) {
            throw new ApplicationException("视频 HLS 转码失败: 空响应");
        }
        String hlsPrefix = Constant.OSS_PATH_ARTICLE_HLS + articleId + "/";
        OssFolderSupport.ensureFolderExists(ossClient, ossConfig, hlsPrefix);
        String playlistKey = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null || name.isBlank() || name.contains("..")) {
                    continue;
                }
                String normalized = name.replace("\\", "/");
                byte[] fileBytes = zis.readAllBytes();
                if (fileBytes.length == 0) {
                    continue;
                }
                String objectKey = ossConfig.objectKey(hlsPrefix, normalized);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(fileBytes.length);
                metadata.setContentType(resolveHlsContentType(normalized));
                ossClient.putObject(ossConfig.getBucketName(), objectKey, new ByteArrayInputStream(fileBytes), metadata);
                if ("index.m3u8".equals(normalized) || normalized.endsWith("/index.m3u8")) {
                    playlistKey = objectKey;
                }
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("HLS zip 上传 OSS 失败 articleId={}", articleId, e);
            throw new ApplicationException("视频 HLS 上传失败: " + e.getMessage());
        }
        if (playlistKey == null || playlistKey.isBlank()) {
            throw new ApplicationException("视频 HLS 转码失败: 缺少 index.m3u8");
        }
        log.info("视频 HLS 转码上传完成 articleId={} 耗时={}ms", articleId, System.currentTimeMillis() - t0);
        return ossConfig.getUrlPrefix() + playlistKey;
    }

    private String resolveHlsContentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (lower.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }
}
