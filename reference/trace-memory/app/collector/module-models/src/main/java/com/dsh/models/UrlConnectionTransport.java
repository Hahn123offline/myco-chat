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
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(Math.max(1, timeoutMs));
            c.setReadTimeout(Math.max(1, timeoutMs));
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
