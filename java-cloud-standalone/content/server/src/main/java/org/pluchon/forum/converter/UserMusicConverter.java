package org.pluchon.forum.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicFavorite;
import org.pluchon.forum.entity.db.UserMusicPlayHistory;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 用户歌曲 Entity → VO
public final class UserMusicConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SERVICE_ERROR_REASON = "内部错误，稍后进行重试";

    private UserMusicConverter() {
    }

    public static String statusCode(Byte status) {
        if (status == null) {
            return "draft";
        }
        if (status == Constant.USER_MUSIC_STATUS_REVIEWING) {
            return "reviewing";
        }
        if (status == Constant.USER_MUSIC_STATUS_PUBLISHED) {
            return "published";
        }
        if (status == Constant.USER_MUSIC_STATUS_REJECTED) {
            return "rejected";
        }
        return "draft";
    }

    public static MusicTrackVO toTrackVO(UserMusic row, boolean includeLyric) {
        if (row == null) {
            return null;
        }
        ReviewDisplay review = parseReviewDisplay(row.getReviewResult());
        MusicTrackVO vo = new MusicTrackVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setMusicKey(row.getMusicKey());
        vo.setTitle(row.getTitle());
        vo.setArtist(row.getArtist());
        vo.setAlbum(row.getAlbum());
        vo.setDurationText(row.getDurationText());
        vo.setCoverUrl(row.getCoverUrl());
        vo.setAudioUrl(row.getAudioUrl());
        vo.setLrcUrl(row.getLrcUrl());
        vo.setStatus(statusCode(row.getStatus()));
        vo.setMoodTags(parseMoodTags(row.getMoodTags()));
        vo.setReviewReason(review.reason());
        vo.setReviewKind(review.kind());
        vo.setAiMatched(Boolean.FALSE);
        vo.setFavorited(Boolean.FALSE);
        if (includeLyric) {
            vo.setLyricText(row.getLyricText());
        }
        return vo;
    }

    public static MusicTrackVO toTrackVO(UserMusicFavorite row) {
        if (row == null) {
            return null;
        }
        return toSnapshotTrackVO(
                row.getMusicKey(),
                row.getTitle(),
                row.getArtist(),
                row.getAlbum(),
                row.getDurationText(),
                row.getCoverUrl(),
                row.getAudioUrl(),
                row.getLrcUrl(),
                Boolean.TRUE);
    }

    public static MusicTrackVO toTrackVO(UserMusicPlayHistory row) {
        if (row == null) {
            return null;
        }
        return toSnapshotTrackVO(
                row.getMusicKey(),
                row.getTitle(),
                row.getArtist(),
                row.getAlbum(),
                row.getDurationText(),
                row.getCoverUrl(),
                row.getAudioUrl(),
                row.getLrcUrl(),
                null);
    }

    private static MusicTrackVO toSnapshotTrackVO(String musicKey,
                                                  String title,
                                                  String artist,
                                                  String album,
                                                  String durationText,
                                                  String coverUrl,
                                                  String audioUrl,
                                                  String lrcUrl,
                                                  Boolean favorited) {
        MusicTrackVO vo = new MusicTrackVO();
        vo.setMusicKey(musicKey);
        vo.setTitle(title);
        vo.setArtist(artist);
        vo.setAlbum(album);
        vo.setDurationText(durationText);
        vo.setCoverUrl(coverUrl);
        vo.setAudioUrl(audioUrl);
        vo.setLrcUrl(lrcUrl);
        vo.setFavorited(favorited);
        vo.setAiMatched(Boolean.FALSE);
        return vo;
    }

    private static List<String> parseMoodTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = MAPPER.readValue(raw, new TypeReference<List<String>>() {
            });
            return tags == null ? List.of() : tags;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static ReviewDisplay parseReviewDisplay(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReviewDisplay.empty();
        }
        try {
            Map<String, Object> review = MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
            String kind = text(review.get("kind"));
            String reason = text(review.get("reason"));
            if ("service_error".equals(kind)) {
                return new ReviewDisplay("service_error", SERVICE_ERROR_REASON);
            }
            if ("violation".equals(kind) && !reason.isBlank()) {
                return new ReviewDisplay("violation", reason);
            }
            if (!reason.isBlank()) {
                return new ReviewDisplay(kind.isBlank() ? "violation" : kind, reason);
            }
            Object reasons = review.get("reasons");
            if (reasons instanceof List<?> list && !list.isEmpty()) {
                String joined = joinReasons(list);
                if (isLegacyServiceError(joined)) {
                    return new ReviewDisplay("service_error", SERVICE_ERROR_REASON);
                }
                return new ReviewDisplay("violation", summarizeLegacyViolation(joined));
            }
            Object legacyReason = review.get("reason");
            if (legacyReason != null) {
                String text = String.valueOf(legacyReason).trim();
                if (isLegacyServiceError(text)) {
                    return new ReviewDisplay("service_error", SERVICE_ERROR_REASON);
                }
                if (!text.isBlank()) {
                    return new ReviewDisplay("violation", summarizeLegacyViolation(text));
                }
            }
        } catch (Exception ignored) {
            return ReviewDisplay.empty();
        }
        return ReviewDisplay.empty();
    }

    private static String summarizeLegacyViolation(String raw) {
        String text = text(raw);
        if (text.contains("音频") && text.contains("文本")) {
            return "文本、音频内容违规";
        }
        if (text.contains("音频")) {
            return "音频内容违规";
        }
        if (text.contains("文本") || text.contains("歌词")) {
            return "文本内容违规";
        }
        return "内容违规";
    }

    private static boolean isLegacyServiceError(String raw) {
        String text = text(raw);
        return text.contains("暂时不可用") || text.contains("需人工复核") || text.contains("内部错误");
    }

    private static String joinReasons(List<?> list) {
        List<String> texts = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                texts.add(String.valueOf(item));
            }
        }
        return String.join("；", texts);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ReviewDisplay(String kind, String reason) {
        private static ReviewDisplay empty() {
            return new ReviewDisplay(null, null);
        }
    }
}
