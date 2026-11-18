package com.dsh.redaction;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;

/**
 * 真实电源状态：BatteryManager + PowerManager。
 * 任何一次系统调用失败都退回"不满足触发条件"，宁可不批处理，也不在错误状态下耗电。
 *
 * 真机排障（task-10）：`BatteryManager.isCharging()` 在部分 ROM/模拟状态下不一定认
 * `dumpsys battery set ac 1`；所以再加一道 sticky `ACTION_BATTERY_CHANGED` 的
 * plugged/status 判据（两个都算充电），并让日志/metrics 暴露最终原因。
 */
public final class AndroidPowerState implements PowerState {

    private final Context app;
    private final BatteryManager battery;
    private final PowerManager power;

    public AndroidPowerState(Context app) {
        this.app = app;
        this.battery = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
        this.power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public boolean isCharging() {
        try {
            if (battery != null && battery.isCharging()) return true;
        } catch (Throwable ignored) {
            // 继续用 sticky intent 兜底
        }
        try {
            Intent i = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return false;
            int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return plugged != 0
                    || status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isScreenOff() {
        try {
            return power != null && !power.isInteractive();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int batteryPercent() {
        try {
            return battery == null ? -1 : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Throwable t) {
            return -1;
        }
    }
}
