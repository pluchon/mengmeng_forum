package org.pluchon.forum.util;

import java.util.Locale;

// 播放量展示文案
public final class MusicPlayCountFormatter {

    private MusicPlayCountFormatter() {
    }

    public static String format(long count) {
        if (count < 1000L) {
            return Long.toString(count);
        }
        double k = count / 1000.0;
        if (k >= 100.0) {
            return String.format(Locale.ROOT, "%.0fk", k);
        }
        String text = String.format(Locale.ROOT, "%.1fk", k);
        return text.endsWith(".0k") ? text.replace(".0k", "k") : text;
    }
}
