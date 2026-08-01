package org.pluchon.forum.common.utils;

import org.pluchon.forum.common.constant.Constant;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 抽奖活动封面OSS路径
// 规范化命名，提高了服务端的健壮与高可用性
public final class LotteryImagePathUtils {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZONE);

    private LotteryImagePathUtils() {
    }

    public static String nowTs() {
        return TS.format(Instant.now());
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
}
