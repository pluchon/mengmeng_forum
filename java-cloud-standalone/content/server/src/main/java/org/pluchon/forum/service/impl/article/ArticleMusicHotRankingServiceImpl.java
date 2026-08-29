package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.common.constant.ForumTimeZone;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicFavorite;
import org.pluchon.forum.entity.db.UserMusicPlayStat;
import org.pluchon.forum.mapper.UserMusicFavoriteMapper;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicPlayStatMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicHotRankingService;
import org.pluchon.forum.util.MusicHotScoreUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 音乐热榜：公式算分 → Redis 蓝绿 ZSet；空榜时回退 DB 重算
@Service
public class ArticleMusicHotRankingServiceImpl implements ArticleMusicHotRankingService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMusicHotRankingServiceImpl.class);
    private static final ZoneId ZONE = ForumTimeZone.ZONE_ID;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private UserMusicPlayStatMapper userMusicPlayStatMapper;

    @Autowired
    private UserMusicFavoriteMapper userMusicFavoriteMapper;

    @Autowired
    private HotMusicRedisOps hotMusicRedisOps;

    @Override
    public void rebuildHotMusicRanking() {
        List<ScoredTrack> ranked = computeRankedTracks();
        Map<String, Double> scores = new LinkedHashMap<>();
        for (ScoredTrack item : ranked) {
            if (item.score() > 0) {
                scores.put(item.musicKey(), item.score());
            }
        }
        if (scores.isEmpty()) {
            log.info("music hot ranking rebuild skipped: no positive scores");
            return;
        }
        hotMusicRedisOps.rebuildBlueGreen(scores);
        log.info("music hot ranking rebuild done size={}", scores.size());
    }

    @Override
    public void refreshTrackScore(String musicKey) {
        if (!StringUtils.hasText(musicKey)) {
            return;
        }
        String key = musicKey.trim();
        UserMusic row = userMusicMapper.selectOne(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getMusicKey, key)
                .ne(UserMusic::getDeleteState, Constant.DELETE_STATE_TRUE)
                .last("LIMIT 1"));
        if (row == null
                || row.getStatus() == null
                || row.getStatus() != Constant.USER_MUSIC_STATUS_PUBLISHED
                || !StringUtils.hasText(row.getAiProfile())) {
            hotMusicRedisOps.remove(key);
            return;
        }
        LocalDate currentWeek = ArticleMusicPlayStatServiceImpl.currentWeekStart();
        long weeklyPlay = resolveWeeklyPlayCount(userMusicPlayStatMapper.selectById(key), currentWeek);
        long weeklyFavorite = countWeeklyFavorites(key, currentWeek);
        double score = MusicHotScoreUtils.computeHotScore(weeklyPlay, weeklyFavorite, row.getCreateTime());
        if (score <= 0) {
            hotMusicRedisOps.remove(key);
            return;
        }
        hotMusicRedisOps.setScore(key, score);
    }

    @Override
    public void removeFromRanking(String musicKey) {
        if (!StringUtils.hasText(musicKey)) {
            return;
        }
        hotMusicRedisOps.remove(musicKey.trim());
    }

    @Override
    public List<String> listHotMusicKeys(int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int safeOffset = Math.max(0, offset);
        ensureRankingReady();
        return hotMusicRedisOps.reverseRange(safeOffset, (long) safeOffset + limit - 1L);
    }

    @Override
    public long countHotMusicKeys() {
        ensureRankingReady();
        return hotMusicRedisOps.size();
    }

    private void ensureRankingReady() {
        if (hotMusicRedisOps.size() > 0) {
            return;
        }
        rebuildHotMusicRanking();
    }

    private List<ScoredTrack> computeRankedTracks() {
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
        LocalDate currentWeek = ArticleMusicPlayStatServiceImpl.currentWeekStart();
        Map<String, UserMusicPlayStat> statMap = loadStatMap(keys);
        Map<String, Long> weeklyFavoriteMap = loadWeeklyFavoriteCountMap(keys, currentWeek);
        List<ScoredTrack> scored = new ArrayList<>();
        for (UserMusic row : rows) {
            if (row == null || !StringUtils.hasText(row.getMusicKey())) {
                continue;
            }
            String key = row.getMusicKey().trim();
            long weeklyPlay = resolveWeeklyPlayCount(statMap.get(key), currentWeek);
            long weeklyFavorite = weeklyFavoriteMap.getOrDefault(key, 0L);
            double score = MusicHotScoreUtils.computeHotScore(weeklyPlay, weeklyFavorite, row.getCreateTime());
            scored.add(new ScoredTrack(key, row.getUserId(), score, weeklyPlay, weeklyFavorite, row.getUpdateTime()));
        }
        scored.sort(Comparator
                .comparingDouble(ScoredTrack::score).reversed()
                .thenComparingLong(ScoredTrack::weeklyPlay).reversed()
                .thenComparingLong(ScoredTrack::weeklyFavorite).reversed()
                .thenComparing(ScoredTrack::updateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return applyAuthorLimit(scored);
    }

    private List<ScoredTrack> applyAuthorLimit(List<ScoredTrack> ranked) {
        int maxPerAuthor = Constant.MUSIC_HOT_AUTHOR_MAX_PER_LIST;
        Map<Long, Integer> taken = new HashMap<>();
        List<ScoredTrack> limited = new ArrayList<>(ranked.size());
        for (ScoredTrack item : ranked) {
            Long authorId = item.userId();
            if (authorId != null && authorId > 0) {
                int count = taken.getOrDefault(authorId, 0);
                if (count >= maxPerAuthor) {
                    continue;
                }
                taken.put(authorId, count + 1);
            }
            limited.add(item);
        }
        return limited;
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

    private Map<String, Long> loadWeeklyFavoriteCountMap(List<String> keys, LocalDate currentWeek) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Date weekStart = Date.from(currentWeek.atStartOfDay(ZONE).toInstant());
        List<UserMusicFavorite> favorites = userMusicFavoriteMapper.selectList(new LambdaQueryWrapper<UserMusicFavorite>()
                .select(UserMusicFavorite::getMusicKey)
                .in(UserMusicFavorite::getMusicKey, keys)
                .eq(UserMusicFavorite::getDeleteState, Constant.DELETE_STATE_FALSE)
                .ge(UserMusicFavorite::getCreateTime, weekStart));
        Map<String, Long> counts = new HashMap<>();
        for (UserMusicFavorite fav : favorites) {
            if (fav == null || !StringUtils.hasText(fav.getMusicKey())) {
                continue;
            }
            counts.merge(fav.getMusicKey().trim(), 1L, Long::sum);
        }
        return counts;
    }

    private long countWeeklyFavorites(String musicKey, LocalDate currentWeek) {
        Date weekStart = Date.from(currentWeek.atStartOfDay(ZONE).toInstant());
        Long count = userMusicFavoriteMapper.selectCount(new LambdaQueryWrapper<UserMusicFavorite>()
                .eq(UserMusicFavorite::getMusicKey, musicKey)
                .eq(UserMusicFavorite::getDeleteState, Constant.DELETE_STATE_FALSE)
                .ge(UserMusicFavorite::getCreateTime, weekStart));
        return count == null ? 0L : count;
    }

    private static long resolveWeeklyPlayCount(UserMusicPlayStat stat, LocalDate currentWeek) {
        if (stat == null || stat.getWeeklyPlayCount() == null || stat.getWeekStart() == null) {
            return 0L;
        }
        LocalDate storedWeek = toLocalDate(stat.getWeekStart());
        if (!Objects.equals(storedWeek, currentWeek)) {
            return 0L;
        }
        return stat.getWeeklyPlayCount();
    }

    private static LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value.toInstant().atZone(ZONE).toLocalDate();
    }

    private record ScoredTrack(
            String musicKey,
            Long userId,
            double score,
            long weeklyPlay,
            long weeklyFavorite,
            Date updateTime) {
    }
}
