package org.pluchon.forum.common.utils;

import org.springframework.util.StringUtils;

import java.util.Date;

/** 游标编解码：createTimeMillis_id，保证排序稳定 */
public final class CursorUtils {

    private static final String SEP = "_";

    private CursorUtils() {
    }

    public record CursorToken(long timeMillis, long id) {
    }

    public static String encode(Date time, Long id) {
        if (time == null || id == null) {
            return null;
        }
        return time.getTime() + SEP + id;
    }

    public static CursorToken decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        String trimmed = cursor.trim();
        int idx = trimmed.lastIndexOf(SEP);
        if (idx <= 0 || idx >= trimmed.length() - 1) {
            throw new IllegalArgumentException("无效游标");
        }
        long timeMillis = Long.parseLong(trimmed.substring(0, idx));
        long id = Long.parseLong(trimmed.substring(idx + 1));
        return new CursorToken(timeMillis, id);
    }
}
