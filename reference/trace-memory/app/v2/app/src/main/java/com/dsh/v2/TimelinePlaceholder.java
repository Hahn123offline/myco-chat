package com.dsh.v2;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** NEVER_CLOUD 的时间线占位：能说类别，不能说内容，且必须占位。 */
public final class TimelinePlaceholder {

    private TimelinePlaceholder() { }

    public static String line(long t0, long t1, String category) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.CHINA);
        String cat = category == null || category.isEmpty() ? "敏感" : category;
        return f.format(new Date(t0)) + "–" + f.format(new Date(t1))
                + "  ██ 有一段敏感活动（" + cat + "），内容不展示 ██";
    }
}
