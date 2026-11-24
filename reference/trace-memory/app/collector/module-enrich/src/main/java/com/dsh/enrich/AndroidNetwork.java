package com.dsh.enrich;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** requireWifi 的实现（没有 ACCESS_NETWORK_STATE 权限时返回 false = 不发）。 */
final class AndroidNetwork implements Enricher.Network {

    private final Context app;

    AndroidNetwork(Context app) {
        this.app = app;
    }

    @Override public boolean isWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Throwable t) {
            // 查询失败按"没有 wifi"处理：requireWifi 下宁可少发，不误发
            return false;
        }
    }
}
