package com.dsh.agent.l2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.agent.ImageBudget;
import com.dsh.agent.MemoryAgentStore;
import com.dsh.agent.ScriptedCaller;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolRegistry;
import com.dsh.agent.ToolResult;
import com.dsh.models.ToolCall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class L2SessionTest {

    private static String body() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append("用户先打开应用，然后进行搜索并查看结果。");
        return b.toString();
    }

    @Test public void 初始上下文是纯文本不含图片() {
        L2Session s = new L2Session("chain1", new FakeBlockSource(16, true));
        String user = s.buildInitialUser();
        assertTrue(user.contains("共 16 条"));
        assertTrue(user.contains("#1 "));
        assertTrue(user.contains("#3 "));
        assertFalse(s.buildInitialUser().contains("image"));
    }

    @Test public void 十六块拼图只发一张图并提交成功() {
        FakeBlockSource src = new FakeBlockSource(16, true);
        L2Session s = new L2Session("c", src);
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(ScriptedCaller.call("m1", "read_mosaic",
                        "{\"block_ids\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]}")),
                ScriptedCaller.toolCalls(ScriptedCaller.call("s1", "submit_description",
                        "{\"title\":\"搜索资料\",\"body\":\"" + body()
                                + "\",\"key_block_ids\":[2,4,5]}")));
        L2Session.Result r = s.run("s1", new MemoryAgentStore(), caller);
        assertTrue(r.error, r.done);
        assertEquals(2, caller.calls());
        assertEquals("第一次请求不能带图", 0,
                ScriptedCaller.imageCount(caller.requests.get(0)));
        assertEquals("16 块必须只发 1 张图", 1,
                ScriptedCaller.imageCount(caller.requests.get(1)));
        assertEquals(1, src.mosaicCalls);
        assertFalse("敏感块不能进拼图", src.lastMosaicIds.contains(3L));
        assertEquals("搜索资料", s.tools().submission().title);
    }

    @Test public void submit的body太短也接受() {
        L2Session s = new L2Session("c", new FakeBlockSource(3, false));
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(ScriptedCaller.call("s1", "submit_description",
                        "{\"title\":\"太短\",\"body\":\"太短\",\"key_block_ids\":[1]}")));
        MemoryAgentStore store = new MemoryAgentStore();
        L2Session.Result r = s.run("s2", store, caller);
        assertTrue(r.error, r.done);
        assertEquals("太短", s.tools().submission().body);
    }

    @Test public void 看图预算6次_第7次被拒绝() {
        L2Tools tools = new L2Tools("c", new FakeBlockSource(4, false));
        ToolRegistry reg = tools.registry();
        ToolContext ctx = new ToolContext(new ImageBudget(6), new HashMap<>());
        for (int i = 0; i < 6; i++) {
            ToolResult r = reg.execute("read_frame", "{\"block_id\":1}", ctx);
            assertTrue("第 " + (i + 1) + " 次应成功", r.isOk());
        }
        ToolResult seventh = reg.execute("read_frame", "{\"block_id\":1}", ctx);
        assertFalse(seventh.isOk());
        assertTrue(seventh.error.contains("看图次数已用完"));
    }

    @Test public void 敏感块的frame被拒绝() {
        L2Tools tools = new L2Tools("c", new FakeBlockSource(4, true));
        ToolResult r = tools.registry().execute("read_frame", "{\"block_id\":3}",
                new ToolContext(new ImageBudget(6), new HashMap<>()));
        assertFalse(r.isOk());
        assertTrue(r.error.contains("屏蔽"));
    }

    @Test public void list_blocks分页() {
        L2Tools tools = new L2Tools("c", new FakeBlockSource(10, false));
        ToolResult r = tools.registry().execute("list_blocks",
                "{\"offset\":5,\"limit\":3}", new ToolContext(new ImageBudget(6), new HashMap<>()));
        assertTrue(r.isOk());
        assertTrue(r.summary.contains("[5,8)"));
        assertTrue(r.summary.contains("#6 "));
        assertFalse(r.summary.contains("#2 "));
    }
}
