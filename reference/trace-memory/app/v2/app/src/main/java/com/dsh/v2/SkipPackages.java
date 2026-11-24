package com.dsh.v2;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.dsh.trace.SkipPolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构性跳过列表（不是语义判断）：
 *  - 自己的包名（设置页/状态页不是"内容"）
 *  - 系统 UI（通知栏/弹层）固定包名
 *  - launcher：用 CATEGORY_HOME 反查，不写死厂商包名
 *  - 输入法：InputMethodManager 已启用列表
 *
 * **系统设置本身不跳过**——用户真的会在设置里翻东西，那是有效记忆。
 */
public final class SkipPackages {

    private static final String[] SYSTEM_UI = {
            "com.android.systemui", "miui.systemui.plugin",
            // 小米「个人助理/负一屏」：HOME 键进入的主屏延伸，不是内容页
            "com.miui.personalassistant"
    };

    private SkipPackages() { }

    public static SkipPolicy build(Context app) {
        Set<String> set = new HashSet<>();
        set.add(app.getPackageName());
        for (String p : SYSTEM_UI) set.add(p);

        try {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo ri = app.getPackageManager()
                    .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                set.add(ri.activityInfo.packageName);
            }
        } catch (Throwable ignored) { }

        try {
            InputMethodManager imm = (InputMethodManager)
                    app.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                List<InputMethodInfo> list = imm.getEnabledInputMethodList();
                if (list != null) {
                    for (InputMethodInfo imi : list) {
                        if (imi != null) set.add(imi.getPackageName());
                    }
                }
            }
        } catch (Throwable ignored) { }

        return SkipPolicy.of(set);
    }
}
