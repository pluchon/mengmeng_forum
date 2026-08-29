package org.pluchon.forum.common.constant;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 全站统一时区。
 *
 * <p>不要依赖 JVM 默认时区：容器基础镜像 eclipse-temurin 默认是 UTC，
 * 而 spring.jackson.time-zone 只管 Jackson 序列化，管不到手写的
 * SimpleDateFormat / DateTimeFormatter，也管不到 @Scheduled 的 cron。
 * 两者混用会出现"消息通知时间对、登录日志时间差 8 小时"这类不一致。
 *
 * <p>凡是 Java 侧把时间格式化成字符串、或按天分组、或定时任务定点，
 * 一律显式使用这里的常量。
 */
public final class ForumTimeZone {

    /** Spring @Scheduled(zone = ...) 只接受字符串 */
    public static final String ID = "Asia/Taipei";

    public static final ZoneId ZONE_ID = ZoneId.of(ID);

    private ForumTimeZone() {
    }

    public static TimeZone timeZone() {
        return TimeZone.getTimeZone(ZONE_ID);
    }
}
