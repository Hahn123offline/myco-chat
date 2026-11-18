package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** adb: am broadcast -a com.dsh.v2.action.REPAIR_PARENTS -p com.dsh.v2 */
public final class ParentRepairReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.REPAIR_PARENTS";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        // 不持有 goAsync：修复可能跑几分钟，广播必须立刻返回
        MergePass.repairParents(context.getApplicationContext(), (ok, message) -> {
            Log.i(MergePass.TAG, "parent repair done=" + ok + " " + message);
        });
    }
}
