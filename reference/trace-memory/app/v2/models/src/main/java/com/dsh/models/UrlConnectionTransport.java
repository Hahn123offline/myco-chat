package com.dsh.models;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 真实 HTTP 实现。只依赖 java.net.HttpURLConnection：
 *  - 安卓自带，不引 OkHttp/Retrofit，体积零增长；
 *  - 请求量很小（一次推理一个请求），连接池收益可以忽略。
 *
 * 这是全模块唯一真正出网的地方。
 */
public final class UrlConnectionTransport implements HttpTransport {

    private static final java.util.Set<HttpURLConnection> ACTIVE =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(
                            new java.util.concurrent.ConcurrentHashMap<>()));

    /** 强制中断所有正在读 socket 的连接；URLConnection 只能这样取消阻塞读。 */
    public static void cancelAllActive() {
        java.util.List<HttpURLConnection> copy = new java.util.ArrayList<>(ACTIVE);
        for (HttpURLConnection c : copy) {
            try { c.disconnect(); } catch (Throwable ignored) { }
        }
        ACTIVE.clear();
    }

    @Override
    public Response post(String url, Map<String, String> headers, byte[] body, int timeoutMs)
            throws Exception {
        return exec("POST", url, headers, body, timeoutMs);
    }

    @Override
    public Response get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        return exec("GET", url, headers, null, timeoutMs);
    }

    private Response exec(String method, String url, Map<String, String> headers,
                          byte[] body, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        ACTIVE.add(c);
        try {
            c.setRequestMethod(method);
            // 连接阶段短超时、读阶段长超时；两者都必须有，否则 InputStream.read 可无限阻塞。
            c.setConnectTimeout(Math.max(1, Math.min(10_000, timeoutMs)));
            c.setReadTimeout(Math.max(120_000, timeoutMs));
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "dsh-models/1");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        c.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
            }
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(status, in == null ? "" : readAll(in));
        } finally {
            ACTIVE.remove(c);
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) >= 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
