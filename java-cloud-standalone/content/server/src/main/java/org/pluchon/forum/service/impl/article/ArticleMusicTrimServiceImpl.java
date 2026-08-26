package org.pluchon.forum.service.impl.article;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.article.MusicTrimResultVO;
import org.pluchon.forum.service.interfaces.article.ArticleMusicTrimService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Locale;

// FFmpeg 音频裁剪
@Slf4j
@Service
public class ArticleMusicTrimServiceImpl implements ArticleMusicTrimService {

    private static final double MIN_TRIM_SECONDS = 1.0;

    @Value("${forum.ffmpeg.internal-key:}")
    private String ffmpegInternalKey;

    @Autowired
    @Qualifier("ffmpegRestTemplate")
    private RestTemplate ffmpegRestTemplate;

    @Override
    public MusicTrimResultVO trim(MultipartFile audio, double startSec, double endSec) {
        validateAudio(audio);
        if (startSec < 0 || endSec <= startSec || endSec - startSec < MIN_TRIM_SECONDS) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "裁剪区间无效，至少保留 1 秒"));
        }

        long t0 = System.currentTimeMillis();
        String baseUrl = System.getenv().getOrDefault("FORUM_FFMPEG_URL", "http://ffmpeg:8099");
        String url = baseUrl.endsWith("/") ? baseUrl + "trim-audio" : baseUrl + "/trim-audio";
        String originalName = audio.getOriginalFilename() == null ? "audio.mp3" : audio.getOriginalFilename();
        log.info("音频开始裁剪 name={} size={}KB start={} end={} ffmpeg={}",
                originalName, audio.getSize() / 1024, startSec, endSec, baseUrl);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new InputStreamResource(audio.getInputStream()) {
                @Override
                public String getFilename() {
                    return originalName;
                }

                @Override
                public long contentLength() {
                    return audio.getSize();
                }
            });
            body.add("startSec", String.valueOf(startSec));
            body.add("endSec", String.valueOf(endSec));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (ffmpegInternalKey != null && !ffmpegInternalKey.isBlank()) {
                headers.set("X-Internal-Key", ffmpegInternalKey);
            }
            HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> resp = ffmpegRestTemplate.postForEntity(url, req, byte[].class);
            byte[] bytes = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0) {
                throw new ApplicationException("音频裁剪失败: 空响应");
            }
            if (bytes.length > Constant.MUSIC_AUDIO_MAX_SIZE) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "裁剪后歌曲不能超过 50MB"));
            }

            HttpHeaders respHeaders = resp.getHeaders();
            String mimeType = respHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
            if (!StringUtils.hasText(mimeType)) {
                mimeType = guessAudioMime(extOf(originalName));
            } else if (mimeType.contains(";")) {
                mimeType = mimeType.substring(0, mimeType.indexOf(';')).trim();
            }

            String fileName = respHeaders.getFirst("X-Audio-Filename");
            if (!StringUtils.hasText(fileName)) {
                fileName = stemOf(originalName) + "_trim." + extOf(originalName);
            }

            int durationSeconds = parseDurationSeconds(respHeaders.getFirst("X-Audio-Duration-Seconds"), endSec - startSec);
            String durationText = respHeaders.getFirst("X-Audio-Duration-Text");
            if (!StringUtils.hasText(durationText)) {
                durationText = formatDuration(durationSeconds);
            }

            MusicTrimResultVO vo = new MusicTrimResultVO();
            vo.setFileName(fileName);
            vo.setMimeType(mimeType);
            vo.setDurationSeconds(durationSeconds);
            vo.setDurationText(durationText);
            vo.setAudioBase64(Base64.getEncoder().encodeToString(bytes));

            log.info("音频裁剪完成 name={} out={}KB duration={} 耗时={}ms",
                    fileName, bytes.length / 1024, durationText, System.currentTimeMillis() - t0);
            return vo;
        } catch (ApplicationException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ApplicationException("音频裁剪队列繁忙，请稍后再试");
            }
            log.error("音频裁剪服务 HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ApplicationException("音频裁剪失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("音频裁剪服务调用失败: {}", e.getMessage());
            throw new ApplicationException("音频裁剪失败: " + e.getMessage());
        }
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请选择要裁剪的歌曲文件"));
        }
        if (file.getSize() > Constant.MUSIC_AUDIO_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌曲不能超过 50MB"));
        }
        String ext = extOf(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!Constant.MUSIC_AUDIO_EXT.contains(ext)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅支持 mp3 / wav / flac / m4a"));
        }
    }

    private static int parseDurationSeconds(String raw, double fallback) {
        if (StringUtils.hasText(raw)) {
            try {
                return Math.max(1, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Math.max(1, (int) Math.round(fallback));
    }

    private static String formatDuration(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    private static String extOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot >= filename.length() - 1) {
            return "mp3";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stemOf(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String guessAudioMime(String ext) {
        return switch (ext) {
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "m4a" -> "audio/mp4";
            default -> "audio/mpeg";
        };
    }
}
