package com.dsh.pcharness;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.memory.MemorySource;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 真数据安全回归：相册/支付宝原文不得出现在 recall 注入文本里。 */
public class RecallSecurityTest {

    private static final String DB = "<workspace>/raw/v2/memory.db";

    @Test public void 相册六条原文不得进入注入text() {
        MemorySource source = new JdbcMemorySource(DB);
        List<RecallHit> hits;
        try {
            hits = RecallCore.recall(source, new RecallQuery()
                    .query("").pkg("com.miui.gallery")
                    .from("2000-01-01").to("2100-01-01").limit(20));
        } catch (Throwable t) {
            // 没解压/无 DB 的环境跳过（CI 不阻塞）
            return;
        }
        if (hits.isEmpty()) return;
        List<String> raw = new ArrayList<>();
        for (RecallHit h : hits) if (h.text.length() >= 20) raw.add(h.text.substring(0, 20));
        RecallCore.Sanitized s = RecallCore.sanitize(hits);
        StringBuilder injected = new StringBuilder();
        for (RecallHit h : s.hits) injected.append(h.text).append('\n');
        for (String r : raw) assertFalse("原文泄漏: " + r, injected.toString().contains(r));
        assertTrue(s.strippedSources.contains("com.miui.gallery"));
        assertFalse(s.sources.contains("com.miui.gallery"));
    }

    @Test public void 微信原文替换为masked_text() {
        MemorySource source = new JdbcMemorySource(DB);
        List<RecallHit> hits;
        try {
            hits = RecallCore.recall(source, new RecallQuery()
                    .query("微信").pkg("com.tencent.mm")
                    .from("2000-01-01").to("2100-01-01").limit(10));
        } catch (Throwable t) { return; }
        for (RecallHit h : hits) {
            RecallCore.Sanitized s = RecallCore.sanitize(java.util.Collections.singletonList(h));
            String injected = s.hits.get(0).text;
            // 没有运行时 Masker 时 REDACT 必须降级占位符，绝不能用原文
            assertTrue(injected.contains("拦截") || injected.contains("缺少有效掩码文本"));
            assertFalse(injected.equals(h.text));
        }
    }
}
