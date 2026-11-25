package dev.myco.android.ui

import android.content.Context

/** 外观偏好：系统/浅色/深色。SharedPreferences 足够，M1 不引入 DataStore 依赖。 */
object ThemePrefs {
    private const val FILE = "myco.appearance"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        return runCatching { ThemeMode.valueOf(raw ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}
