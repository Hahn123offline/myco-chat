package com.dsh.v2;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimelinePlaceholderTest {

    @Test public void 占位能说类别不泄露内容() {
        String s = TimelinePlaceholder.line(0, 60_000, "支付类");
        assertTrue(s.contains("有一段敏感活动"));
        assertTrue(s.contains("支付类"));
        assertTrue(s.contains("内容不展示"));
        assertTrue(s.contains("██"));
    }
}
