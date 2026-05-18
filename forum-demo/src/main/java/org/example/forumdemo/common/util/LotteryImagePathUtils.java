package org.example.forumdemo.common.util;

import org.example.forumdemo.common.constant.Constant;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 抽奖活动封面 / 奖品图 OSS 相对路径命名建议（与上传端约定一致）.
 * <ul>
 *     <li>活动封面目录: {@link Constant#OSS_PATH_LOTTERY_ACTIVITY}，对象名 {@code {activityId}_{publisherId}_{yyyyMMddHHmmss}.{ext}}</li>
 *     <li>奖品图目录: {@link Constant#OSS_PATH_LOTTERY_PRIZE}，对象名 {@code {activityId}_{prizeId}_{yyyyMMddHHmmss}.{ext}}</li>
 * </ul>
 */
public final class LotteryImagePathUtils {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZONE);

    private LotteryImagePathUtils() {
    }

    public static String nowTs() {
        return TS.format(java.time.Instant.now());
    }

    public static String activityCoverObjectName(long activityId, long publisherId, String ts, String ext) {
        return activityId + "_" + publisherId + "_" + ts + "." + ext;
    }

    public static String activityCoverRelative(long activityId, long publisherId, String ts, String ext) {
        return Constant.OSS_PATH_LOTTERY_ACTIVITY + activityCoverObjectName(activityId, publisherId, ts, ext);
    }

    public static String prizeImageObjectName(long activityId, long prizeId, String ts, String ext) {
        return activityId + "_" + prizeId + "_" + ts + "." + ext;
    }

    public static String prizeImageRelative(long activityId, long prizeId, String ts, String ext) {
        return Constant.OSS_PATH_LOTTERY_PRIZE + prizeImageObjectName(activityId, prizeId, ts, ext);
    }
}
