package com.dsh.agent.l2;

import static org.junit.Assert.assertTrue;

import com.dsh.agent.MemoryAgentStore;
import com.dsh.agent.ScriptedCaller;
import com.dsh.agent.AgentEvent;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 生成一条完整 L2 会话转录（供人工检查；输出在 agent/build/l2_demo_transcript.txt）。 */
public class L2DemoTranscriptTest {

    @Test public void 生成完整转录() throws Exception {
        FakeBlockSource src = new FakeBlockSource(16, true);
        L2Session s = new L2Session("demo-chain", src);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 8; i++) body.append("用户打开应用，先查看列表，然后用搜索框检索内容，最后查看结果。");
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(ScriptedCaller.call("c1", "list_blocks",
                        "{\"offset\":0,\"limit\":40}")),
                ScriptedCaller.toolCalls(ScriptedCaller.call("c2", "read_mosaic",
                        "{\"block_ids\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]}")),
                ScriptedCaller.toolCalls(ScriptedCaller.call("c3", "submit_description",
                        "{\"title\":\"搜索并查看结果\",\"body\":\"" + body
                                + "\",\"key_block_ids\":[2,4,5]}")));
        MemoryAgentStore store = new MemoryAgentStore();
        L2Session.Result r = s.run("demo-session", store, caller);
        assertTrue(r.error, r.done);

        File out = new File("build/l2_demo_transcript.txt");
        out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write("chain=demo-chain blocks=" + src.blocks.size()
                    + " turns=" + caller.calls() + "\n");
            w.write("final title=" + r.submission.title + "\n");
            w.write("final body=" + r.submission.body + "\n");
            w.write("key_block_ids=" + r.submission.keyBlockIds + "\n\n");
            for (AgentEvent e : store.events("demo-session")) {
                w.write("#" + e.seq + " [" + e.type + "] " + e.payload + "\n");
            }
        }
    }
}
