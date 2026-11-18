package com.dsh.agent.l2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.dsh.agent.MemoryAgentStore;
import com.dsh.agent.UsageRecordCatcher;
import com.dsh.egress.EgressGate;
import com.dsh.egress.MemoryAudit;
import com.dsh.models.Capability;
import com.dsh.models.CostClass;
import com.dsh.models.ModelProvider;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.ModelRouter;
import com.dsh.models.ProviderKind;
import com.dsh.models.ToolCall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** L2 会话走 :models + :egress 的集成测试（假 provider，不联网）。 */
public class L2RouterIntegrationTest {

    static final class ScriptProvider implements ModelProvider {
        final List<ModelResponse> script;
        int i;
        ScriptProvider(ModelResponse... r) { script = Arrays.asList(r); }
        @Override public String id() { return "cloud-fake"; }
        @Override public ProviderKind kind() { return ProviderKind.CLOUD; }
        @Override public Set<Capability> caps() { return Collections.singleton(Capability.JSON); }
        @Override public int maxContext() { return 64000; }
        @Override public CostClass costClass() { return CostClass.CHEAP; }
        @Override public ModelResponse complete(ModelRequest request) {
            return script.get(Math.min(i++, script.size() - 1));
        }
    }

    @Test public void L2走路由与出网闸并记录usage() {
        ScriptProvider provider = new ScriptProvider(
                ModelResponse.ok("", "fake", "cloud-fake").withToolCalls(
                        Collections.singletonList(new ToolCall("c1", "read_mosaic",
                                "{\"block_ids\":[1,2,3]}"))),
                ModelResponse.ok("", "fake", "cloud-fake").withToolCalls(
                        Collections.singletonList(new ToolCall("c2", "submit_description",
                                "{\"title\":\"看图后描述\",\"body\":\"" + body()
                                        + "\",\"key_block_ids\":[1,2]}"))));
        MemoryAudit audit = new MemoryAudit();
        UsageRecordCatcher usage = new UsageRecordCatcher();
        EgressGate gate = new EgressGate(null, audit);
        ModelRouter router = new ModelRouter(Collections.singletonList(provider), false,
                gate, usage);

        L2Session session = new L2Session("chain", new FakeBlockSource(3, false));
        L2Session.Result result = session.run("s", new MemoryAgentStore(),
                new RouterLlmCaller(router));
        assertTrue(result.error, result.done);
        assertEquals("看图后描述", result.submission.title);
        assertTrue("至少两次模型调用 usage", usage.records.size() >= 2);
        assertTrue("每次出网都有审计", audit.size() >= 2);
        for (int i = 0; i < audit.size(); i++) {
            assertTrue("集成路径不应被闸拒绝: " + audit.entries.get(i).reason,
                    audit.entries.get(i).allowed);
        }
    }

    private static String body() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append("用户打开应用并查看结果，内容客观记录。");
        return b.toString();
    }
}
