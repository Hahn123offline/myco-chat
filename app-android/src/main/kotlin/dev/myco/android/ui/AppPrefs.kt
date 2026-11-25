package dev.myco.android.ui

import android.content.Context

/** 应用形态：chat = 单焦点（打开最近会话，历史走顶栏入口）；work = 多会话列表。 */
enum class AppMode { CHAT, WORK }

object AppPrefs {
    private const val FILE = "myco.app"
    private const val KEY_MODE = "app_mode"

    fun loadMode(context: Context): AppMode {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return runCatching { AppMode.valueOf(raw ?: "CHAT") }.getOrDefault(AppMode.CHAT)
    }

    fun saveMode(context: Context, mode: AppMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
    }
}
