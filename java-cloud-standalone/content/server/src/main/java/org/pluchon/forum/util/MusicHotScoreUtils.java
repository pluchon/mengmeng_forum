package org.pluchon.forum.util;

import org.pluchon.forum.common.constant.Constant;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// 音乐大厅本周热榜综合分：周播放 + 周收藏，带时间衰减与新歌加成
public final class MusicHotScoreUtils {

    private MusicHotScoreUtils() {
    }

    public static double computeHotScore(long weeklyPlayCount, long weeklyFavoriteCount, Date createTime) {
        long safePlay = Math.max(0L, weeklyPlayCount);
        long safeFavorite = Math.max(0L, weeklyFavoriteCount);
        double base = safePlay * Constant.MUSIC_HOT_SCORE_WEIGHT_PLAY
                + safeFavorite * Constant.MUSIC_HOT_SCORE_WEIGHT_FAVORITE;
        if (base <= 0) {
            return 0;
        }
        long ageHours = resolveAgeHours(createTime);
        double ageDays = ageHours / 24.0;
        double decay = 1.0 / (1.0 + ageDays / Constant.MUSIC_HOT_SCORE_DECAY_DAYS);
        double boost = ageHours <= Constant.MUSIC_HOT_NEW_TRACK_HOURS
                ? Constant.MUSIC_HOT_NEW_TRACK_BOOST
                : 1.0;
        return base * decay * boost;
    }

    private static long resolveAgeHours(Date createTime) {
        if (createTime == null) {
            return 0L;
        }
        long hours = Duration.between(createTime.toInstant(), Instant.now()).toHours();
        return Math.max(0L, hours);
    }
}
