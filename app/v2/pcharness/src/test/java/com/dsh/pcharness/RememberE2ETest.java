package com.dsh.pcharness;

import com.dsh.memory.MemItem;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.Assert.*;

/** D2 里程碑：chat 在场记忆 vs trace 翻记录，两种人称。 */
public class RememberE2ETest {

    @Test public void remember写在场的note_召回两种人称() throws Exception {
        java.nio.file.Path tmp = Files.createTempFile("mem", ".db");
        Files.copy(Paths.get("<workspace>/raw/v2/memory.db"), tmp,
                StandardCopyOption.REPLACE_EXISTING);
        JdbcMemorySource source = new JdbcMemorySource(tmp.toString());
        long id = source.remember("在改微信代理问题", "note", null,
                java.util.Collections.singletonList("代理"), "chat", "记一下");
        MemItem m = source.get(id);
        assertNotNull(m);
        assertEquals("chat", m.namespace);
        assertEquals(1.5, m.weight, 1e-9);
        assertNull(m.key);
        assertTrue(com.dsh.memory.MemoryPolicy.present(m));

        List<RecallHit> hits = RecallCore.recall(source, new RecallQuery()
                .query("代理那个事").from("2000-01-01").to("2100-01-01").limit(8));
        boolean foundNote = false;
        for (RecallHit h : hits) if (h.itemId == id) foundNote = true;
        assertTrue(foundNote);
        String text = RecallCore.render(hits);
        assertTrue(text.contains("那会儿我们在聊这个"));

        List<RecallHit> trace = RecallCore.recall(source, new RecallQuery()
                .query("概率论").from("2000-01-01").to("2100-01-01").limit(3));
        assertFalse(trace.isEmpty());
        String traceText = RecallCore.render(trace);
        assertTrue(traceText.contains("我翻记录看到的，那会儿我没在看"));

        String status = source.status();
        assertTrue(status.contains("chat 1"));
        assertTrue(status.contains("在场（你当时在）：1"));
    }
}
