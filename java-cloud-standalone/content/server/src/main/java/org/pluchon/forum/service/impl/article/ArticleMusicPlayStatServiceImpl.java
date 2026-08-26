package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.entity.db.UserMusicPlayStat;
import org.pluchon.forum.mapper.UserMusicPlayStatMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicHotRankingService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicPlayStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

// 歌曲全站播放统计累加
@Service
public class ArticleMusicPlayStatServiceImpl implements ArticleMusicPlayStatService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private UserMusicPlayStatMapper userMusicPlayStatMapper;

    @Autowired
    private ArticleMusicHotRankingService articleMusicHotRankingService;

    @Override
    public void incrementPlayCount(String musicKey) {
        if (!StringUtils.hasText(musicKey)) {
            return;
        }
        String key = musicKey.trim();
        LocalDate weekStart = currentWeekStart();
        Date weekStartDate = Date.valueOf(weekStart);
        java.util.Date now = new java.util.Date();
        UserMusicPlayStat stat = userMusicPlayStatMapper.selectById(key);
        if (stat == null) {
            UserMusicPlayStat row = new UserMusicPlayStat();
            row.setMusicKey(key);
            row.setPlayCount(1L);
            row.setWeeklyPlayCount(1L);
            row.setWeekStart(weekStartDate);
            row.setUpdateTime(now);
            userMusicPlayStatMapper.insert(row);
            refreshHotScoreQuietly(key);
            return;
        }
        long playCount = stat.getPlayCount() == null ? 0L : stat.getPlayCount();
        long weeklyPlayCount = stat.getWeeklyPlayCount() == null ? 0L : stat.getWeeklyPlayCount();
        LocalDate storedWeek = toLocalDate(stat.getWeekStart());
        if (storedWeek == null || !Objects.equals(storedWeek, weekStart)) {
            weeklyPlayCount = 1L;
        } else {
            weeklyPlayCount += 1L;
        }
        stat.setPlayCount(playCount + 1L);
        stat.setWeeklyPlayCount(weeklyPlayCount);
        stat.setWeekStart(weekStartDate);
        stat.setUpdateTime(now);
        userMusicPlayStatMapper.updateById(stat);
        refreshHotScoreQuietly(key);
    }

    public static LocalDate currentWeekStart() {
        return LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void refreshHotScoreQuietly(String musicKey) {
        try {
            articleMusicHotRankingService.refreshTrackScore(musicKey);
        } catch (Exception ignored) {
            // 热榜刷新失败不影响播放计数
        }
    }

    private static LocalDate toLocalDate(java.util.Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value.toInstant().atZone(ZONE).toLocalDate();
    }
}
