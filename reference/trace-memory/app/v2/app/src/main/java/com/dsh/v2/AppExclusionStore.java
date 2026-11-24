package com.dsh.v2;

import android.content.Context;
import android.content.SharedPreferences;

import com.dsh.egress.AppExclusionPolicy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 三级策略本地持久化（绝不上云）。 */
public final class AppExclusionStore {

    private static final String PREFS = "app_policies";
    private static final String KEY_LEVELS = "levels";   // "pkg=LEVEL"

    private AppExclusionStore() { }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void load(Context ctx) {
        AppExclusionPolicy.clearOverrides();
        Set<String> levels = prefs(ctx).getStringSet(KEY_LEVELS, null);
        if (levels == null) return;
        for (String s : levels) {
            int i = s.lastIndexOf('=');
            if (i <= 0) continue;
            String pkg = s.substring(0, i);
            try {
                AppExclusionPolicy.setLevel(pkg,
                        AppExclusionPolicy.Level.valueOf(s.substring(i + 1)));
            } catch (Throwable ignored) { }
        }
    }

    public static synchronized void setLevel(Context ctx, String pkg,
                                             AppExclusionPolicy.Level level) {
        AppExclusionPolicy.setLevel(pkg, level);
        persist(ctx);
    }

    public static synchronized void restore(Context ctx) {
        AppExclusionPolicy.clearOverrides();
        prefs(ctx).edit().clear().apply();
    }

    /** 旧 API 兼容 */
    public static void add(Context ctx, String pkg) {
        setLevel(ctx, pkg, AppExclusionPolicy.Level.NEVER_CLOUD);
    }

    public static void remove(Context ctx, String pkg) {
        setLevel(ctx, pkg, AppExclusionPolicy.Level.NORMAL);
    }

    private static void persist(Context ctx) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, AppExclusionPolicy.Level> e
                : AppExclusionPolicy.overrides().entrySet()) {
            out.add(e.getKey() + "=" + e.getValue().name());
        }
        prefs(ctx).edit().putStringSet(KEY_LEVELS, out).apply();
    }
}
