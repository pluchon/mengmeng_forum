package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicFavorite;
import org.pluchon.forum.entity.db.UserMusicPlayHistory;
import org.pluchon.forum.entity.dto.AiMusicTasteRecommendRequest;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.mapper.UserMusicFavoriteMapper;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicPlayHistoryMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendMixService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendRefreshService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendSlateService;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// 双周片单：AI 品味图优先，失败则 RULE 混排落库
@Service
public class ArticleMusicRecommendRefreshServiceImpl implements ArticleMusicRecommendRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMusicRecommendRefreshServiceImpl.class);
    private static final int MAX_USERS_PER_RUN = 300;
    private static final int CANDIDATE_LIMIT = 200;
    private static final int SLATE_SIZE = 30;
    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_RULE = "RULE";

    @Autowired
    private UserMusicFavoriteMapper userMusicFavoriteMapper;

    @Autowired
    private UserMusicPlayHistoryMapper userMusicPlayHistoryMapper;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private ArticleMusicRecommendMixService articleMusicRecommendMixService;

    @Autowired
    private ArticleMusicRecommendSlateService articleMusicRecommendSlateService;

    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean shouldRunBiweeklyRefresh() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.of(java.util.Locale.CHINA);
        int week = today.get(wf.weekOfWeekBasedYear());
        int bucket = ((week + 1) / 2) * 2;
        return week == bucket;
    }

    @Override
    public void refreshBiweeklySlates() {
        if (!shouldRunBiweeklyRefresh()) {
            log.info("skip music taste slate refresh: not biweekly bucket Sunday");
            return;
        }
        String periodKey = articleMusicRecommendSlateService.currentPeriodKey();
        List<Long> userIds = listActiveUserIds();
        int ok = 0;
        int fail = 0;
        for (Long userId : userIds) {
            try {
                refreshOne(userId, periodKey);
                ok++;
            } catch (Exception ex) {
                fail++;
                log.warn("music taste slate refresh failed userId={} period={}", userId, periodKey, ex);
            }
        }
        log.info("music taste slate refresh done period={} users={} ok={} fail={}",
                periodKey, userIds.size(), ok, fail);
    }

    private void refreshOne(Long userId, String periodKey) {
        List<String> aiKeys = tryAiKeys(userId);
        if (!aiKeys.isEmpty()) {
            articleMusicRecommendSlateService.saveSlate(userId, periodKey, SOURCE_AI, aiKeys);
            return;
        }
        List<MusicTrackVO> ruleTracks = articleMusicRecommendMixService.buildPersonalizedRecommend(userId, SLATE_SIZE);
        List<String> ruleKeys = new ArrayList<>();
        if (ruleTracks != null) {
            for (MusicTrackVO track : ruleTracks) {
                if (track != null && StringUtils.hasText(track.getMusicKey())) {
                    ruleKeys.add(track.getMusicKey().trim());
                }
            }
        }
        if (ruleKeys.isEmpty()) {
            return;
        }
        articleMusicRecommendSlateService.saveSlate(userId, periodKey, SOURCE_RULE, ruleKeys);
    }

    private List<String> tryAiKeys(Long userId) {
        List<Map<String, Object>> candidates = buildCandidates(userId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        AiMusicTasteRecommendRequest request = new AiMusicTasteRecommendRequest();
        request.setUserId(userId);
        request.setFavorites(buildFavoriteSignals(userId));
        request.setRecentPlays(buildRecentPlaySignals(userId));
        request.setExtras(List.of());
        request.setCandidates(candidates);
        try {
            AiHubMusicMatchResultVO result = contentAiGatewayService.recommendMusicTaste(userId, request);
            if (result == null || result.getMusicKeys() == null || result.getMusicKeys().isEmpty()) {
                return List.of();
            }
            Set<String> allowed = new HashSet<>();
            for (Map<String, Object> row : candidates) {
                Object key = row.get("musicKey");
                if (key != null) {
                    allowed.add(String.valueOf(key).trim());
                }
            }
            List<String> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String key : result.getMusicKeys()) {
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                String trimmed = key.trim();
                if (!allowed.contains(trimmed) || !seen.add(trimmed)) {
                    continue;
                }
                out.add(trimmed);
                if (out.size() >= SLATE_SIZE) {
                    break;
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("music taste AI call failed userId={}", userId, ex);
            return List.of();
        }
    }

    private List<Long> listActiveUserIds() {
        Date since = Date.from(Instant.now().minus(28, ChronoUnit.DAYS));
        Set<Long> ids = new LinkedHashSet<>();
        List<UserMusicFavorite> favorites = userMusicFavoriteMapper.selectList(new LambdaQueryWrapper<UserMusicFavorite>()
                .select(UserMusicFavorite::getUserId)
                .eq(UserMusicFavorite::getDeleteState, Constant.DELETE_STATE_FALSE)
                .ge(UserMusicFavorite::getUpdateTime, since)
                .last("LIMIT 2000"));
        for (UserMusicFavorite row : favorites) {
            if (row != null && row.getUserId() != null && row.getUserId() > 0) {
                ids.add(row.getUserId());
            }
        }
        List<UserMusicPlayHistory> plays = userMusicPlayHistoryMapper.selectList(new LambdaQueryWrapper<UserMusicPlayHistory>()
                .select(UserMusicPlayHistory::getUserId)
                .eq(UserMusicPlayHistory::getDeleteState, Constant.DELETE_STATE_FALSE)
                .ge(UserMusicPlayHistory::getUpdateTime, since)
                .last("LIMIT 2000"));
        for (UserMusicPlayHistory row : plays) {
            if (row != null && row.getUserId() != null && row.getUserId() > 0) {
                ids.add(row.getUserId());
            }
        }
        List<Long> out = new ArrayList<>(ids);
        if (out.size() > MAX_USERS_PER_RUN) {
            return out.subList(0, MAX_USERS_PER_RUN);
        }
        return out;
    }

    private List<Map<String, Object>> buildFavoriteSignals(Long userId) {
        List<UserMusicFavorite> favorites = userMusicFavoriteMapper.selectList(new LambdaQueryWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getUserId, userId)
                .eq(UserMusicFavorite::getDeleteState, Constant.DELETE_STATE_FALSE)
                .orderByDesc(UserMusicFavorite::getUpdateTime)
                .last("LIMIT 40"));
        Map<String, UserMusic> musicMap = loadMusicMap(favorites.stream()
                .map(UserMusicFavorite::getMusicKey)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserMusicFavorite fav : favorites) {
            if (fav == null || !StringUtils.hasText(fav.getMusicKey())) {
                continue;
            }
            String key = fav.getMusicKey().trim();
            out.add(signalRow(key, musicMap.get(key), fav.getArtist()));
        }
        return out;
    }

    private List<Map<String, Object>> buildRecentPlaySignals(Long userId) {
        Date since = Date.from(Instant.now().minus(14, ChronoUnit.DAYS));
        List<UserMusicPlayHistory> plays = userMusicPlayHistoryMapper.selectList(new LambdaQueryWrapper<UserMusicPlayHistory>()
                .eq(UserMusicPlayHistory::getUserId, userId)
                .eq(UserMusicPlayHistory::getDeleteState, Constant.DELETE_STATE_FALSE)
                .ge(UserMusicPlayHistory::getUpdateTime, since)
                .orderByDesc(UserMusicPlayHistory::getUpdateTime)
                .last("LIMIT 40"));
        Map<String, UserMusic> musicMap = loadMusicMap(plays.stream()
                .map(UserMusicPlayHistory::getMusicKey)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserMusicPlayHistory play : plays) {
            if (play == null || !StringUtils.hasText(play.getMusicKey())) {
                continue;
            }
            String key = play.getMusicKey().trim();
            out.add(signalRow(key, musicMap.get(key), null));
        }
        return out;
    }

    private List<Map<String, Object>> buildCandidates(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // 向量召回优先，不足再补最近发布曲目
        try {
            String query = buildTasteQuery(userId);
            if (StringUtils.hasText(query)) {
                List<String> vectorKeys = contentAiGatewayService.ragVectorSearchMusic(query);
                if (vectorKeys != null) {
                    Map<String, UserMusic> musicMap = loadMusicMap(vectorKeys.stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList());
                    for (String key : vectorKeys) {
                        if (!StringUtils.hasText(key)) {
                            continue;
                        }
                        String trimmed = key.trim();
                        UserMusic row = musicMap.get(trimmed);
                        if (row == null || !seen.add(trimmed)) {
                            continue;
                        }
                        out.add(candidateRow(row));
                        if (out.size() >= CANDIDATE_LIMIT) {
                            return out;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("music vector candidate recall failed userId={}", userId, ex);
        }
        List<UserMusic> rows = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .isNotNull(UserMusic::getAiProfile)
                .ne(UserMusic::getAiProfile, "")
                .ne(UserMusic::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(UserMusic::getUpdateTime)
                .last("LIMIT " + CANDIDATE_LIMIT));
        for (UserMusic row : rows) {
            if (row == null || !StringUtils.hasText(row.getMusicKey())) {
                continue;
            }
            String trimmed = row.getMusicKey().trim();
            if (!seen.add(trimmed)) {
                continue;
            }
            out.add(candidateRow(row));
            if (out.size() >= CANDIDATE_LIMIT) {
                break;
            }
        }
        return out;
    }

    private String buildTasteQuery(Long userId) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (Map<String, Object> row : buildFavoriteSignals(userId)) {
            appendTastePart(parts, row);
            if (parts.size() >= 12) {
                break;
            }
        }
        for (Map<String, Object> row : buildRecentPlaySignals(userId)) {
            appendTastePart(parts, row);
            if (parts.size() >= 20) {
                break;
            }
        }
        return String.join(" ", parts);
    }

    private static void appendTastePart(Set<String> parts, Map<String, Object> row) {
        if (row == null) {
            return;
        }
        addTasteToken(parts, row.get("title"));
        addTasteToken(parts, row.get("artist"));
        addTasteToken(parts, row.get("genre"));
        addTasteToken(parts, row.get("mood"));
    }

    private static void addTasteToken(Set<String> parts, Object raw) {
        if (raw == null) {
            return;
        }
        String text = String.valueOf(raw).trim();
        if (!text.isEmpty()) {
            parts.add(text);
        }
    }

    private Map<String, Object> candidateRow(UserMusic row) {
        Map<String, Object> item = new HashMap<>();
        item.put("musicKey", row.getMusicKey().trim());
        item.put("title", row.getTitle() == null ? "" : row.getTitle());
        item.put("artist", row.getArtist() == null ? "" : row.getArtist());
        item.put("genre", extractGenre(row.getAiProfile()));
        item.put("moodTags", parseMoodTags(row.getMoodTags(), row.getAiProfile()));
        item.put("aiProfile", row.getAiProfile() == null ? "" : row.getAiProfile());
        return item;
    }

    private Map<String, Object> signalRow(String musicKey, UserMusic music, String fallbackArtist) {
        Map<String, Object> row = new HashMap<>();
        row.put("musicKey", musicKey);
        if (music != null) {
            row.put("title", music.getTitle() == null ? "" : music.getTitle());
            row.put("artist", StringUtils.hasText(music.getArtist()) ? music.getArtist()
                    : (fallbackArtist == null ? "" : fallbackArtist));
            row.put("genre", extractGenre(music.getAiProfile()));
            List<String> moods = parseMoodTags(music.getMoodTags(), music.getAiProfile());
            row.put("mood", moods.isEmpty() ? "" : moods.get(0));
        } else {
            row.put("title", "");
            row.put("artist", fallbackArtist == null ? "" : fallbackArtist);
            row.put("genre", "");
            row.put("mood", "");
        }
        return row;
    }

    private Map<String, UserMusic> loadMusicMap(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<UserMusic> rows = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .in(UserMusic::getMusicKey, keys)
                .ne(UserMusic::getDeleteState, Constant.DELETE_STATE_TRUE));
        Map<String, UserMusic> map = new HashMap<>();
        for (UserMusic row : rows) {
            if (row != null && StringUtils.hasText(row.getMusicKey())) {
                map.put(row.getMusicKey().trim(), row);
            }
        }
        return map;
    }

    private String extractGenre(String aiProfileJson) {
        try {
            if (!StringUtils.hasText(aiProfileJson)) {
                return "";
            }
            Map<String, Object> map = objectMapper.readValue(aiProfileJson, new TypeReference<>() {
            });
            Object genre = map.get("genre");
            return genre == null ? "" : String.valueOf(genre).trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private List<String> parseMoodTags(String moodTagsJson, String aiProfileJson) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            if (StringUtils.hasText(moodTagsJson)) {
                List<String> tags = objectMapper.readValue(moodTagsJson, new TypeReference<>() {
                });
                for (String tag : tags) {
                    addMood(seen, out, tag);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            if (StringUtils.hasText(aiProfileJson)) {
                Map<String, Object> map = objectMapper.readValue(aiProfileJson, new TypeReference<>() {
                });
                Object moodTags = map.get("moodTags");
                if (moodTags instanceof List<?> list) {
                    for (Object item : list) {
                        addMood(seen, out, item);
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return out;
    }

    private static void addMood(Set<String> seen, List<String> out, Object raw) {
        if (raw == null) {
            return;
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || "null".equals(text) || !seen.add(text)) {
            return;
        }
        out.add(text);
    }
}
