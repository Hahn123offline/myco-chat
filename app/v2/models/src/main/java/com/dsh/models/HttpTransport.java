package com.dsh.models;

import java.util.Map;

/**
 * HTTP 传输抽象。
 *
 * 把它单独抽出来的目的：provider 的协议逻辑（URL / header / body / 响应解析）
 * 可以在单元测试里用假 transport 完整跑一遍，**绝不真连网**。
 */
public interface HttpTransport {

    Response post(String url, Map<String, String> headers, byte[] body, int timeoutMs) throws Exception;

    default Response get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        throw new UnsupportedOperationException("此 transport 不支持 GET");
    }

    final class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        public boolean isOk() { return status >= 200 && status < 300; }

        @Override public String toString() { return "HTTP " + status + ": " + body; }
    }
}
