package org.pluchon.forum.service.impl.article;

import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.article.MusicParseResultVO;
import org.pluchon.forum.service.interfaces.article.ArticleMusicParseService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

// 使用 jaudiotagger 读取内嵌元数据（含 FLAC Picture Block / ID3 APIC / Vorbis LYRICS）
@Slf4j
@Service
public class ArticleMusicParseServiceImpl implements ArticleMusicParseService {

    private static final long COVER_BASE64_MAX_BYTES = 2L * 1024 * 1024;

    static {
        // jaudiotagger 默认打大量 INFO 日志
        Logger.getLogger("org.jaudiotagger").setLevel(Level.WARNING);
    }

    @Override
    public MusicParseResultVO parse(MultipartFile audio) {
        validateAudio(audio);
        String original = audio.getOriginalFilename() == null ? "audio.bin" : audio.getOriginalFilename();
        String ext = extOf(original);
        Path temp = null;
        try {
            temp = Files.createTempFile("forum-music-parse-", "." + ext);
            audio.transferTo(temp);
            AudioFile audioFile = AudioFileIO.read(temp.toFile());
            Tag tag = audioFile.getTag();
            AudioHeader header = audioFile.getAudioHeader();

            MusicParseResultVO vo = new MusicParseResultVO();
            if (tag != null) {
                vo.setTitle(trimToNull(safeField(tag, FieldKey.TITLE)));
                vo.setArtist(trimToNull(firstNonBlank(
                        safeField(tag, FieldKey.ARTIST),
                        safeField(tag, FieldKey.ALBUM_ARTIST))));
                vo.setAlbum(trimToNull(safeField(tag, FieldKey.ALBUM)));
                String lyrics = firstNonBlank(
                        safeField(tag, FieldKey.LYRICS),
                        safeTagFirst(tag, "LYRICS"),
                        safeTagFirst(tag, "UNSYNCEDLYRICS"),
                        safeTagFirst(tag, "SYNCEDLYRICS"));
                if (!StringUtils.hasText(lyrics)) {
                    lyrics = null;
                } else if (lyrics.length() > Constant.MUSIC_LYRIC_TEXT_MAX_LEN) {
                    lyrics = lyrics.substring(0, Constant.MUSIC_LYRIC_TEXT_MAX_LEN);
                }
                vo.setLyricText(trimToNull(lyrics));
                fillCover(vo, tag);
            }
            if (header != null) {
                int seconds = Math.max(0, header.getTrackLength());
                vo.setDurationSeconds(seconds);
                vo.setDurationText(formatDuration(seconds));
            }
            vo.setHasCover(Boolean.TRUE.equals(vo.getHasCover()));
            if (!StringUtils.hasText(vo.getTitle())) {
                vo.setTitle(stemOf(original));
            }
            return vo;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("音频元数据解析失败 name={}: {}", original, e.getMessage());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "无法解析该音频的内嵌信息，请手动填写或改用 mp3/flac"));
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private void fillCover(MusicParseResultVO vo, Tag tag) {
        try {
            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null) {
                vo.setHasCover(false);
                return;
            }
            byte[] data = artwork.getBinaryData();
            if (data == null || data.length == 0) {
                vo.setHasCover(false);
                return;
            }
            if (data.length > COVER_BASE64_MAX_BYTES) {
                log.info("内嵌封面过大 size={}KB，跳过返回", data.length / 1024);
                vo.setHasCover(false);
                return;
            }
            String mime = artwork.getMimeType();
            if (!StringUtils.hasText(mime)) {
                mime = guessImageMime(data);
            }
            if (mime != null && mime.contains(";")) {
                mime = mime.substring(0, mime.indexOf(';')).trim();
            }
            if (!StringUtils.hasText(mime) || !mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
                mime = "image/jpeg";
            }
            vo.setHasCover(true);
            vo.setCoverMimeType(mime);
            vo.setCoverBase64(Base64.getEncoder().encodeToString(data));
        } catch (Exception e) {
            log.warn("读取内嵌封面失败: {}", e.getMessage());
            vo.setHasCover(false);
        }
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请选择要解析的歌曲文件"));
        }
        if (file.getSize() > Constant.MUSIC_AUDIO_MAX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "歌曲不能超过 50MB"));
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = extOf(name);
        if (!Constant.MUSIC_AUDIO_EXT.contains(ext)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "仅支持 mp3 / wav / flac / m4a"));
        }
    }

    private static String safeField(Tag tag, FieldKey key) {
        try {
            return tag.getFirst(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeTagFirst(Tag tag, String key) {
        try {
            return tag.getFirst(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String formatDuration(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    private static String extOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot >= filename.length() - 1) {
            return "bin";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stemOf(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String guessImageMime(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (data.length >= 8
                && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }
        if (data.length >= 6
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
