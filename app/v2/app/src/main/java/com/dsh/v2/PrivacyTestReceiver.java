package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** adb: am broadcast -a com.dsh.v2.action.PRIVACY_SELFTEST -p com.dsh.v2 */
public final class PrivacyTestReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.PRIVACY_SELFTEST";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        PrivacySelfTest.run(context.getApplicationContext());
    }
}
