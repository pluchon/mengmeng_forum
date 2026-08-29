package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.common.constant.ForumTimeZone;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicFavorite;
import org.pluchon.forum.entity.db.UserMusicPlayHistory;
import org.pluchon.forum.entity.db.UserMusicPlayStat;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.mapper.UserMusicFavoriteMapper;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicPlayHistoryMapper;
import org.pluchon.forum.mapper.UserMusicPlayStatMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendMixService;
import org.pluchon.forum.util.MusicPlayCountFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 日常非 AI 混排：收藏相似 70% / 近播 15% / 同风+上新+周热 15%
@Service
public class ArticleMusicRecommendMixServiceImpl implements ArticleMusicRecommendMixService {

    private static final ZoneId ZONE = ForumTimeZone.ZONE_ID;
    private static final int DEFAULT_LIMIT = 30;
    private static final int FAVORITE_QUOTA = 21;
    private static final int RECENT_QUOTA = 5;
    private static final int STYLE_QUOTA = 2;
    private static final int NEW_QUOTA = 1;
    private static final int HOT_QUOTA = 1;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private UserMusicFavoriteMapper userMusicFavoriteMapper;

    @Autowired
    private UserMusicPlayHistoryMapper userMusicPlayHistoryMapper;

    @Autowired
    private UserMusicPlayStatMapper userMusicPlayStatMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<MusicTrackVO> buildPersonalizedRecommend(Long userId, int limit) {
        int target = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 60);
        List<CatalogTrack> catalog = loadCatalog();
        if (catalog.isEmpty()) {
            return List.of();
        }
        if (userId == null || userId <= 0) {
            return coldStart(catalog, target);
        }

        List<UserMusicFavorite> favorites = userMusicFavoriteMapper.selectList(new LambdaQueryWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getUserId, userId)
                .eq(UserMusicFavorite::getDeleteState, Constant.DELETE_STATE_FALSE)
                .orderByDesc(UserMusicFavorite::getUpdateTime)
                .last("LIMIT 80"));
        Date since = Date.from(Instant.now().minus(14, ChronoUnit.DAYS));
        List<UserMusicPlayHistory> recentPlays = userMusicPlayHistoryMapper.selectList(new LambdaQueryWrapper<UserMusicPlayHistory>()
                .eq(UserMusicPlayHistory::getUserId, userId)
                .eq(UserMusicPlayHistory::getDeleteState, Constant.DELETE_STATE_FALSE)
                .ge(UserMusicPlayHistory::getUpdateTime, since)
                .orderByDesc(UserMusicPlayHistory::getUpdateTime)
                .last("LIMIT 60"));
        if (favorites.isEmpty() && recentPlays.isEmpty()) {
            return coldStart(catalog, target);
        }

        Set<String> favoriteKeys = new HashSet<>();
        Map<String, Integer> seedGenre = new HashMap<>();
        Map<String, Integer> seedMood = new HashMap<>();
        Set<String> seedArtists = new HashSet<>();
        for (UserMusicFavorite fav : favorites) {
            if (fav == null || !StringUtils.hasText(fav.getMusicKey())) {
                continue;
            }
            String key = fav.getMusicKey().trim();
            favoriteKeys.add(key);
            if (StringUtils.hasText(fav.getArtist())) {
                seedArtists.add(fav.getArtist().trim().toLowerCase(Locale.ROOT));
            }
            CatalogTrack hit = findByKey(catalog, key);
            if (hit != null) {
                accumulateProfile(seedGenre, seedMood, hit.profile());
            }
        }

        Set<String> picked = new HashSet<>();
        List<CatalogTrack> out = new ArrayList<>();

        List<CatalogTrack> similar = catalog.stream()
                .filter(item -> !favoriteKeys.contains(item.key()))
                .sorted(Comparator
                        .comparingInt((CatalogTrack item) -> similarityScore(item, seedGenre, seedMood, seedArtists))
                        .reversed()
                        .thenComparingLong(CatalogTrack::weeklyPlayCount).reversed())
                .toList();
        appendQuota(out, picked, similar, FAVORITE_QUOTA);

        List<CatalogTrack> recentPool = new ArrayList<>();
        for (UserMusicPlayHistory row : recentPlays) {
            if (row == null || !StringUtils.hasText(row.getMusicKey())) {
                continue;
            }
            CatalogTrack hit = findByKey(catalog, row.getMusicKey().trim());
            if (hit != null) {
                recentPool.add(hit);
            }
        }
        appendQuota(out, picked, recentPool, RECENT_QUOTA);

        List<CatalogTrack> stylePool = catalog.stream()
                .filter(item -> !picked.contains(item.key()) && !favoriteKeys.contains(item.key()))
                .sorted(Comparator
                        .comparingInt((CatalogTrack item) -> styleOnlyScore(item, seedGenre, seedMood))
                        .reversed()
                        .thenComparingLong(CatalogTrack::playCount).reversed())
                .toList();
        appendQuota(out, picked, stylePool, STYLE_QUOTA);

        Date newSince = Date.from(Instant.now().minus(14, ChronoUnit.DAYS));
        List<CatalogTrack> newPool = catalog.stream()
                .filter(item -> !picked.contains(item.key()))
                .filter(item -> item.updateTime() != null && !item.updateTime().before(newSince))
                .sorted(Comparator.comparing(CatalogTrack::updateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        appendQuota(out, picked, newPool, NEW_QUOTA);

        List<CatalogTrack> hotPool = catalog.stream()
                .filter(item -> !picked.contains(item.key()))
                .sorted(Comparator.comparingLong(CatalogTrack::weeklyPlayCount).reversed()
                        .thenComparingLong(CatalogTrack::playCount).reversed())
                .toList();
        appendQuota(out, picked, hotPool, HOT_QUOTA);

        if (out.size() < target) {
            List<CatalogTrack> filler = catalog.stream()
                    .filter(item -> !picked.contains(item.key()))
                    .sorted(Comparator.comparing(CatalogTrack::updateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            appendQuota(out, picked, filler, target - out.size());
        }
        return toVoList(out, target);
    }

    private List<MusicTrackVO> coldStart(List<CatalogTrack> catalog, int target) {
        Date newSince = Date.from(Instant.now().minus(14, ChronoUnit.DAYS));
        List<CatalogTrack> mixed = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        List<CatalogTrack> hot = catalog.stream()
                .sorted(Comparator.comparingLong(CatalogTrack::weeklyPlayCount).reversed()
                        .thenComparingLong(CatalogTrack::playCount).reversed())
                .toList();
        appendQuota(mixed, picked, hot, Math.max(1, target * 2 / 3));
        List<CatalogTrack> neu = catalog.stream()
                .filter(item -> !picked.contains(item.key()))
                .filter(item -> item.updateTime() != null && !item.updateTime().before(newSince))
                .sorted(Comparator.comparing(CatalogTrack::updateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        appendQuota(mixed, picked, neu, target - mixed.size());
        if (mixed.size() < target) {
            appendQuota(mixed, picked, catalog, target - mixed.size());
        }
        return toVoList(mixed, target);
    }

    private List<CatalogTrack> loadCatalog() {
        List<UserMusic> rows = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .isNotNull(UserMusic::getAiProfile)
                .ne(UserMusic::getAiProfile, "")
                .ne(UserMusic::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> keys = rows.stream()
                .map(UserMusic::getMusicKey)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        Map<String, UserMusicPlayStat> statMap = loadStatMap(keys);
        LocalDate currentWeek = ArticleMusicPlayStatServiceImpl.currentWeekStart();
        List<CatalogTrack> out = new ArrayList<>();
        for (UserMusic row : rows) {
            if (row == null || !StringUtils.hasText(row.getMusicKey())) {
                continue;
            }
            String key = row.getMusicKey().trim();
            UserMusicPlayStat stat = statMap.get(key);
            long playCount = stat == null || stat.getPlayCount() == null ? 0L : stat.getPlayCount();
            long weekly = resolveWeeklyPlayCount(stat, currentWeek);
            ProfileTokens profile = parseProfile(row.getAiProfile(), row.getMoodTags());
            out.add(new CatalogTrack(key, row, profile, playCount, weekly, row.getUpdateTime()));
        }
        return out;
    }

    private Map<String, UserMusicPlayStat> loadStatMap(List<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<UserMusicPlayStat> stats = userMusicPlayStatMapper.selectList(new LambdaQueryWrapper<UserMusicPlayStat>()
                .in(UserMusicPlayStat::getMusicKey, keys));
        Map<String, UserMusicPlayStat> map = new HashMap<>();
        for (UserMusicPlayStat stat : stats) {
            if (stat != null && StringUtils.hasText(stat.getMusicKey())) {
                map.put(stat.getMusicKey().trim(), stat);
            }
        }
        return map;
    }

    private static long resolveWeeklyPlayCount(UserMusicPlayStat stat, LocalDate currentWeek) {
        if (stat == null || stat.getWeeklyPlayCount() == null || stat.getWeekStart() == null) {
            return 0L;
        }
        LocalDate storedWeek = stat.getWeekStart().toInstant().atZone(ZONE).toLocalDate();
        if (!Objects.equals(storedWeek, currentWeek)) {
            return 0L;
        }
        return stat.getWeeklyPlayCount();
    }

    private ProfileTokens parseProfile(String aiProfileJson, String moodTagsJson) {
        Set<String> genres = new HashSet<>();
        Set<String> moods = new HashSet<>();
        try {
            if (StringUtils.hasText(aiProfileJson)) {
                Map<String, Object> map = objectMapper.readValue(aiProfileJson, new TypeReference<>() {
                });
                addToken(genres, map.get("genre"));
                Object moodTags = map.get("moodTags");
                if (moodTags instanceof List<?> list) {
                    for (Object item : list) {
                        addToken(moods, item);
                    }
                }
                addToken(moods, map.get("energy"));
            }
        } catch (Exception ignored) {
            // 画像损坏时降级
        }
        try {
            if (StringUtils.hasText(moodTagsJson)) {
                List<String> tags = objectMapper.readValue(moodTagsJson, new TypeReference<>() {
                });
                for (String tag : tags) {
                    addToken(moods, tag);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return new ProfileTokens(genres, moods);
    }

    private static void addToken(Set<String> bag, Object raw) {
        if (raw == null) {
            return;
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || "null".equals(text)) {
            return;
        }
        for (String part : text.split("[,，/|]")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                bag.add(token);
            }
        }
    }

    private static void accumulateProfile(Map<String, Integer> genre, Map<String, Integer> mood, ProfileTokens profile) {
        if (profile == null) {
            return;
        }
        for (String g : profile.genres()) {
            genre.merge(g, 1, Integer::sum);
        }
        for (String m : profile.moods()) {
            mood.merge(m, 1, Integer::sum);
        }
    }

    private static int similarityScore(CatalogTrack item, Map<String, Integer> seedGenre,
                                       Map<String, Integer> seedMood, Set<String> seedArtists) {
        int score = styleOnlyScore(item, seedGenre, seedMood);
        String artist = item.row().getArtist();
        if (StringUtils.hasText(artist) && seedArtists.contains(artist.trim().toLowerCase(Locale.ROOT))) {
            score += 8;
        }
        return score;
    }

    private static int styleOnlyScore(CatalogTrack item, Map<String, Integer> seedGenre, Map<String, Integer> seedMood) {
        int score = 0;
        for (String g : item.profile().genres()) {
            score += seedGenre.getOrDefault(g, 0) * 3;
        }
        for (String m : item.profile().moods()) {
            score += seedMood.getOrDefault(m, 0) * 2;
        }
        return score;
    }

    private static CatalogTrack findByKey(List<CatalogTrack> catalog, String key) {
        for (CatalogTrack item : catalog) {
            if (Objects.equals(item.key(), key)) {
                return item;
            }
        }
        return null;
    }

    private static void appendQuota(List<CatalogTrack> out, Set<String> picked, List<CatalogTrack> pool, int quota) {
        if (quota <= 0 || pool == null || pool.isEmpty()) {
            return;
        }
        int added = 0;
        for (CatalogTrack item : pool) {
            if (item == null || !StringUtils.hasText(item.key()) || picked.contains(item.key())) {
                continue;
            }
            out.add(item);
            picked.add(item.key());
            added++;
            if (added >= quota) {
                return;
            }
        }
    }

    private static List<MusicTrackVO> toVoList(List<CatalogTrack> tracks, int target) {
        List<MusicTrackVO> out = new ArrayList<>();
        for (CatalogTrack item : tracks) {
            if (out.size() >= target) {
                break;
            }
            MusicTrackVO vo = UserMusicConverter.toTrackVO(item.row(), false);
            if (vo == null) {
                continue;
            }
            vo.setPlayCount(item.playCount());
            vo.setPlayCountText(MusicPlayCountFormatter.format(item.playCount()));
            out.add(vo);
        }
        return out;
    }

    private record ProfileTokens(Set<String> genres, Set<String> moods) {
    }

    private record CatalogTrack(String key, UserMusic row, ProfileTokens profile,
                                long playCount, long weeklyPlayCount, Date updateTime) {
    }
}
