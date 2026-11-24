package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ProviderTest {

    private static ProviderConfig cloud() {
        return new ProviderConfig("cloud", ProviderConfig.Type.OPENAI,
                "https://api.example.com/", "sk-test", "deepseek-chat", 30_000);
    }

    private static ProviderConfig local() {
        return new ProviderConfig("local", ProviderConfig.Type.LOCAL,
                "http://127.0.0.1:8080", "", "minicpm-v-4.7-1b", 120_000);
    }

    private static LlmRequest req() {
        return LlmRequest.of("redact", "sys", "user");
    }

    @Test public void 云端POST到v1chat_completions_并带Bearer() throws Exception {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("deepseek-chat", "ok"));
        OpenAiCompatProvider p = new OpenAiCompatProvider(cloud(), t);

        LlmResponse r = p.complete(req());

        assertTrue(r.error, r.isOk());
        assertEquals("ok", r.text);
        assertEquals("https://api.example.com/v1/chat/completions", t.lastUrl);
        assertEquals("Bearer sk-test", t.lastHeaders.get("Authorization"));
        assertEquals("application/json", t.lastHeaders.get("Content-Type"));
        String sent = OpenAiChatCodec.utf8(t.lastBody);
        assertEquals("deepseek-chat", Json.get(Json.parse(sent), "model"));
    }

    @Test public void 云端HTTP错误返回error而不是抛异常() throws Exception {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.postHandler = (url, headers, body) ->
                new HttpTransport.Response(401, "{\"error\":{\"message\":\"bad key\"}}");
        LlmResponse r = new OpenAiCompatProvider(cloud(), t).complete(req());
        assertFalse(r.isOk());
        assertTrue(r.error.contains("401"));
        assertTrue(r.error.contains("bad key"));
    }

    @Test public void 本地provider把jpeg发成base64_data_uri() throws Exception {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("minicpm-v-4.7-1b", "深港驾校小米"));
        LocalLlamaProvider p = new LocalLlamaProvider(local(), t);

        LlmResponse r = p.complete(LlmRequest.builder("title")
                .userPrompt("这页叫什么").jpeg(new byte[]{1, 2, 3}).build());

        assertTrue(r.error, r.isOk());
        String sent = OpenAiChatCodec.utf8(t.lastBody);
        assertTrue(sent.contains("data:image/jpeg;base64,AQID"));
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", t.lastUrl);
    }

    @Test public void 本地provider没有apiKey时不发Authorization() throws Exception {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("m", "ok"));
        new LocalLlamaProvider(local(), t).complete(req());
        assertNull(t.lastHeaders.get("Authorization"));
    }

    @Test public void provider没配model时不发请求直接报错() throws Exception {
        ProviderConfig c = new ProviderConfig("cloud", ProviderConfig.Type.OPENAI,
                "https://api.example.com", "sk", "", 30_000);
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        LlmResponse r = new OpenAiCompatProvider(c, t).complete(req());
        assertFalse(r.isOk());
        assertTrue(r.error.contains("model"));
        assertEquals(0, t.posts.get());
    }

    @Test public void 探测本地provider用GET_v1models() {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.getHandler = (url, headers, body) -> new HttpTransport.Response(200, "{\"data\":[]}");
        HttpProviderProbe probe = new HttpProviderProbe(t);
        assertTrue(probe.probe(local()).up);
        assertEquals("http://127.0.0.1:8080/v1/models", t.lastUrl);
    }

    @Test public void 探测失败要带原因_不假装OK() {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.getHandler = (url, headers, body) -> new HttpTransport.Response(503, "server busy");
        ProbeResult r = new HttpProviderProbe(t).probe(local());
        assertFalse(r.up);
        assertEquals("http-503", r.kind);
        assertEquals(503, r.httpStatus);
        assertTrue(r.detail, r.detail.contains("server busy"));
    }

    @Test public void 探测异常要带异常类名与message() {
        TestSupport.FakeTransport broken = new TestSupport.FakeTransport();
        broken.getHandler = (url, headers, body) -> {
            throw new java.net.ConnectException("Connection refused");
        };
        ProbeResult r = new HttpProviderProbe(broken).probe(local());
        assertFalse(r.up);
        assertEquals("connect-refused", r.kind);
        assertTrue(r.detail, r.detail.contains("ConnectException"));
        assertTrue(r.detail, r.detail.contains("Connection refused"));
    }

    @Test public void 主线程联网异常被识别为可诊断原因() {
        // 用同简单类名的本地异常模拟 android.os.NetworkOnMainThreadException（JVM 里不能构造 android stub）
        ProbeResult r = ProbeResult.of(new NetworkOnMainThreadException("The application may be doing too much work on its main thread."));
        assertFalse(r.up);
        assertEquals("main-thread-network", r.kind);
        assertTrue(r.detail, r.detail.contains("NetworkOnMainThreadException"));
        assertTrue(r.forLog(), r.forLog().contains("err="));
    }

    private static final class NetworkOnMainThreadException extends RuntimeException {
        NetworkOnMainThreadException(String msg) { super(msg); }
    }

    @Test public void 请求体使用UTF8编码_中文不乱码() throws Exception {
        TestSupport.FakeTransport t = new TestSupport.FakeTransport();
        t.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("m", "ok"));
        new OpenAiCompatProvider(cloud(), t).complete(
                LlmRequest.of("title", "中文系统提示", "中文用户提示"));
        String sent = new String(t.lastBody, StandardCharsets.UTF_8);
        assertTrue(sent.contains("中文用户提示"));
    }

    @Test public void ProviderConfig规范化baseUrl与model() {
        ProviderConfig c = new ProviderConfig("x", ProviderConfig.Type.OPENAI,
                " https://a.example.com/// ", "  k  ", "  m  ", 0);
        assertEquals("https://a.example.com", c.baseUrl);
        assertEquals("k", c.apiKey);
        assertEquals("m", c.model);
        assertEquals(60_000, c.timeoutMs);
    }
}
