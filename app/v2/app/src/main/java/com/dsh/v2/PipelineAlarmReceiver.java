package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 闹钟触发：默认时间到就跑；用户开启条件时，不满足则 15 分钟后再试。
 * 窗口 = 上次成功运行到现在（无记录则 24h）。
 */
public final class PipelineAlarmReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !PipelineScheduler.ACTION.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        if (!PipelineScheduler.conditionsMet(app)) {
            Log.i(PipelineScheduler.TAG, "alarm fired but conditions not met; retry in 15min");
            PipelineScheduler.scheduleRetry(app);
            return;
        }
        int hours = PipelineStatusStore.hoursSinceLastSuccess(app);
        // 不持有 goAsync：流水线可能跑几分钟，alarm 广播必须立刻返回
        L0L1Pipeline.run(app, hours, (ok, message) -> {
            Log.i(PipelineScheduler.TAG, "alarm pipeline done=" + ok + " " + message);
            PipelineStatusStore.saveFromLastRun(app, ok, message);
            PipelineNotifier.notifyResult(app, ok, message);
            // 合并 pass 已停用：日叙事将用指针承担合并语义
            DayNarrativeRunner.runYesterday(app, (dok, dmsg) -> {
                Log.i(DayNarrativeRunner.TAG, "alarm day narrative done=" + dok + " " + dmsg);
                PipelineScheduler.scheduleNext(app);
            });
        });
    }
}
