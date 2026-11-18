package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 稳定文本指纹：过滤噪声、取前 8、固定顺序拼接哈希。 */
public class TextFingerprintTest {

    @Test public void 跳过纯数字纯标点与长度小于2() {
        assertFalse(TextFingerprint.useful("123"));
        assertFalse(TextFingerprint.useful("12345"));
        assertFalse(TextFingerprint.useful("！！！"));
        assertFalse(TextFingerprint.useful("。"));
        assertFalse(TextFingerprint.useful("a"));        // 长度 1
        assertFalse(TextFingerprint.useful("  a  "));
        assertFalse(TextFingerprint.useful(""));
        assertFalse(TextFingerprint.useful(null));
        assertTrue(TextFingerprint.useful("推荐"));
        assertTrue(TextFingerprint.useful("GPT-6 发布"));
        assertTrue(TextFingerprint.useful("a1"));
    }

    @Test public void 只取前8个有用节点并保持顺序() {
        List<String> texts = Arrays.asList(
                "节点一", "节点二", "节点三", "节点四", "节点五",
                "节点六", "节点七", "节点八", "节点九");
        List<String> useful = TextFingerprint.firstUseful(texts);
        assertEquals(TextFingerprint.N, useful.size());
        assertEquals("节点一", useful.get(0));
        assertEquals("节点八", useful.get(7));
        String joined = TextFingerprint.joined(texts);
        assertTrue(joined.contains("\u0001"));
        assertTrue(joined.startsWith("节点一\u0001节点二"));
        assertFalse("第 9 个不进哈希", joined.contains("节点九"));
    }

    @Test public void 过滤后不足N个也能工作() {
        List<String> texts = Arrays.asList("123", "！！", "a", "推荐", "热点");
        assertEquals(2, TextFingerprint.firstUseful(texts).size());
        assertTrue(TextFingerprint.hash(texts).length() == 16);
    }

    @Test public void 同一组文本同顺序哈希稳定() {
        List<String> a = Arrays.asList("推荐", "热点", "视频");
        List<String> b = Arrays.asList("推荐", "热点", "视频");
        assertEquals(TextFingerprint.hash(a), TextFingerprint.hash(b));
        assertEquals(16, TextFingerprint.hash(a).length());
    }

    @Test public void 顺序变化哈希不同() {
        List<String> a = Arrays.asList("推荐", "热点", "视频");
        List<String> b = Arrays.asList("热点", "推荐", "视频");
        assertNotEquals(TextFingerprint.hash(a), TextFingerprint.hash(b));
    }

    @Test public void 没有有用文本时返回空串() {
        assertEquals("", TextFingerprint.joined(Arrays.asList("123", "！！", "a")));
        assertEquals("", TextFingerprint.hash(Collections.emptyList()));
        assertEquals("", TextFingerprint.hash(null));
    }

    @Test public void 噪声节点不影响哈希() {
        List<String> clean = Arrays.asList("推荐", "热点");
        List<String> noisy = Arrays.asList("123", "推荐", "！！", "热点", "a");
        assertEquals(TextFingerprint.hash(clean), TextFingerprint.hash(noisy));
    }
}
