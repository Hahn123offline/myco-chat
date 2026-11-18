package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChainOutcomeTest {

    @Test public void 只有resolved能断言解决() {
        assertTrue(ChainOutcome.canAssertResolved("resolved"));
        assertFalse(ChainOutcome.canAssertResolved("unknown"));
        assertFalse(ChainOutcome.canAssertResolved("in_progress"));
        assertFalse(ChainOutcome.canAssertResolved("no_action"));
    }

    @Test public void 解析非法值回退unknown() {
        assertEquals(ChainOutcome.UNKNOWN, ChainOutcome.parse(null));
        assertEquals(ChainOutcome.UNKNOWN, ChainOutcome.parse("weird"));
        assertEquals(ChainOutcome.NO_ACTION, ChainOutcome.parse("no_action"));
    }

    @Test public void 从body保守推断() {
        assertEquals(ChainOutcome.NO_ACTION,
                ChainOutcome.inferFromBody("截至记录结束，没有出现实际修改的动作"));
        assertEquals(ChainOutcome.RESOLVED,
                ChainOutcome.inferFromBody("代理恢复正常，页面可以打开了"));
        assertEquals(ChainOutcome.IN_PROGRESS,
                ChainOutcome.inferFromBody("用户反复查看设置，最后离开页面"));
    }
}
