package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** adb: am broadcast -a com.dsh.v2.action.RUN_MERGE -p com.dsh.v2 */
public final class MergeReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.RUN_MERGE";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        // 合并 pass 已停用：日叙事用 event_ids 指针承担合并语义。
        // 代码保留，未来可作为独立「主题聚合」工具；现有父链数据保留不删。
        Log.i(MergePass.TAG, "RUN_MERGE disabled: merge pass retired in favor of day narrative");
    }
}
