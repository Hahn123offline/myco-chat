package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * pageId 单一路径（task-20）：`act:<activity>#h8:<hash>` / 无 activity 时 `shot:<hash>`。
 * title 已降为字段，不参与身份；身份不能因 title 有无而翻。
 */
public class SceneResolverTest {

    private static String hash(String stableText) {
        return SceneResolver.sha1(stableText == null ? "" : stableText, 16);
    }

    @Test public void 主路径_activity加前N节点文本哈希() {
        String joined = TextFingerprint.joined(Arrays.asList("推荐", "热点"));
        String a = SceneResolver.resolve("com.bili.Main", joined, "shot123");
        String b = SceneResolver.resolve("com.bili.Main", joined, "shot999");
        assertEquals("同一 activity + 同一组文本必须稳定", a, b);
        assertEquals("act:com.bili.Main#h8:" + hash(joined), a);
    }

    @Test public void 同一activity内不同文本_不同pageId() {
        String a = SceneResolver.resolve("com.bili.Main",
                TextFingerprint.joined(Arrays.asList("推荐", "热点")), null);
        String b = SceneResolver.resolve("com.bili.Main",
                TextFingerprint.joined(Arrays.asList("推荐", "关注")), null);
        assertTrue(!a.equals(b));
    }

    @Test public void 节点顺序变化_不同pageId() {
        String a = SceneResolver.resolve("com.bili.Main",
                TextFingerprint.joined(Arrays.asList("推荐", "热点")), null);
        String b = SceneResolver.resolve("com.bili.Main",
                TextFingerprint.joined(Arrays.asList("热点", "推荐")), null);
        assertTrue(!a.equals(b));
    }

    @Test public void 没有有用文本也走同一路径_对空串求哈希() {
        String a = SceneResolver.resolve("com.bili.Main", "", "shot123");
        assertEquals("act:com.bili.Main#h8:" + hash(""), a);
        assertEquals("同一 activity 的空文本身份稳定",
                a, SceneResolver.resolve("com.bili.Main", null, "shot999"));
        assertTrue("不能退化成 activity-only 级（身份会翻）", !a.equals("act:com.bili.Main"));
    }

    @Test public void 没有activity时才用截图哈希() {
        assertEquals("shot:abc123", SceneResolver.resolve(null, "任意文本", "abc123"));
        assertEquals("shot:abc123", SceneResolver.resolve("", "任意文本", "abc123"));
    }

    @Test public void 全空返回unknown() {
        assertEquals(SceneResolver.UNKNOWN, SceneResolver.resolve(null, null, null));
        assertEquals(SceneResolver.UNKNOWN, SceneResolver.resolve("", "", ""));
    }

    @Test public void 文本原样参与哈希_不做归一化() {
        String a = SceneResolver.resolve("a", "标题", null);
        String b = SceneResolver.resolve("a", "标题 ", null);
        String c = SceneResolver.resolve("a", "标题（1）", null);
        assertTrue(!a.equals(b));
        assertTrue(!a.equals(c));
        assertTrue(!SceneResolver.resolve("a", "AB", null)
                .equals(SceneResolver.resolve("a", "ab", null)));
    }

    @Test public void sha1稳定且可截断() {
        String x = SceneResolver.sha1("hello", 16);
        String y = SceneResolver.sha1("hello", 16);
        assertEquals(x, y);
        assertEquals(16, x.length());
        assertNotNull(SceneResolver.sha1("", 8));
    }
}
