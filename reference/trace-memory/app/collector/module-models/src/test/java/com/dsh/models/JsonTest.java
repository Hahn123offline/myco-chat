package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class JsonTest {

    @Test public void 解析嵌套对象与数组() {
        Object root = Json.parse("{\"a\":1,\"b\":[true,false,null,\"x\"],\"c\":{\"d\":2.5}}");
        Map<String, Object> m = Json.asObject(root);
        assertEquals(1L, ((Number) m.get("a")).longValue());
        List<Object> b = Json.asArray(m.get("b"));
        assertEquals(Boolean.TRUE, b.get(0));
        assertEquals(Boolean.FALSE, b.get(1));
        assertNull(b.get(2));
        assertEquals("x", b.get(3));
        assertEquals(2.5, (Double) (Json.asObject(m.get("c")).get("d")), 1e-9);
    }

    @Test public void 包含中文与转义的字符串能往返() {
        String s = "标题：深港驾校\"小米\"\n第二行\t制表";
        String quoted = Json.quote(s);
        Object back = Json.parse("{\"k\":" + quoted + "}");
        assertEquals(s, Json.get(back, "k"));
    }

    @Test public void 控制字符被转义成合法JSON() {
        String quoted = Json.quote("a\u0001b");
        assertTrue(quoted.contains("\\u0001"));
        Object back = Json.parse(quoted);
        assertEquals("a\u0001b", back);
    }

    @Test public void 非法JSON抛异常而不是静默返回() {
        try {
            Json.parse("{\"a\":");
            fail("应该抛异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("JSON"));
        }
    }

    @Test public void 数字区分整数与小数() {
        assertEquals(42L, ((Number) Json.parse("42")).longValue());
        assertEquals(3.14, (Double) Json.parse("3.14"), 1e-9);
    }
}
