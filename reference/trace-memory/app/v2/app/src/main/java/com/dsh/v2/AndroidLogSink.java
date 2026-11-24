package com.dsh.v2;

import android.util.Log;

import com.dsh.core.SimpleLog;

/** 把 :core 的日志 sink 接到 logcat；文件兜底由 SimpleLog 负责。 */
public final class AndroidLogSink implements SimpleLog.Sink {

    private static final String TAG = "DSHHost";

    @Override public void onLine(String level, String tag, String msg, Throwable t) {
        String line = tag + ": " + msg;
        if ("E".equals(level)) Log.e(TAG, line, t);
        else if ("W".equals(level)) Log.w(TAG, line);
        else Log.i(TAG, line);
    }
}
