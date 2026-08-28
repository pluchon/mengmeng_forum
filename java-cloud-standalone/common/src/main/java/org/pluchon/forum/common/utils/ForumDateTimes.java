package org.pluchon.forum.common.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

// 论坛统一使用东八区墙钟
public final class ForumDateTimes {

    public static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Taipei");

    private ForumDateTimes() {
    }

    public static void useShanghaiAsDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_SHANGHAI));
    }

    // 当前时刻，供写入 MySQL datetime 配合 JDBC serverTimezone Asia/Taipei
    public static Date now() {
        return Date.from(ZonedDateTime.now(ZONE_SHANGHAI).toInstant());
    }
}
