package com.dsh.agent.l1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.agent.ScriptedCaller;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class L1GroupingTest {

    private static List<L1Session> sessions(int n) {
        List<L1Session> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add(new L1Session(i, i * 60_000L, i * 60_000L + 30_000,
                    i % 2 == 0 ? "b" : "a", 2, "首" + i, "末" + i));
        }
        return out;
    }

    @Test public void 允许交错且互斥覆盖() {
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText(
                "{\"subchains\":[{\"title\":\"同一件事\",\"session_ids\":[1,3,5]},"
                        + "{\"title\":\"另一件事\",\"session_ids\":[2,4]}]}"));
        L1Grouping.Result r = L1Grouping.group(sessions(5), caller);
        assertFalse(r.fallback);
        assertEquals(2, r.subchains.size());
        assertEquals(Arrays.asList(1, 3, 5), r.subchains.get(0).sessionIndexes);
        assertEquals("thinking 关闭", "disabled", caller.requests.get(0).thinking);
        assertEquals(0, caller.requests.get(0).maxTokens);
    }

    @Test public void 漏项退化为一条覆盖全部() {
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText(
                "{\"subchains\":[{\"title\":\"A\",\"session_ids\":[1,2]}]}"));
        L1Grouping.Result r = L1Grouping.group(sessions(3), caller);
        assertTrue(r.fallback);
        assertEquals(1, r.subchains.size());
        assertEquals(3, r.subchains.get(0).sessionIndexes.size());
    }

    @Test public void 重复或越界也退化() {
        assertTrue(L1Grouping.parse("{\"subchains\":[{\"title\":\"A\",\"session_ids\":[1,1]}]}",
                2, sessions(2)).fallback);
        assertTrue(L1Grouping.parse("{\"subchains\":[{\"title\":\"A\",\"session_ids\":[1,9]}]}",
                2, sessions(2)).fallback);
    }

    @Test public void 坏JSON退化且不丢会话() {
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText("not json"));
        L1Grouping.Result r = L1Grouping.group(sessions(4), caller);
        assertTrue(r.fallback);
        assertEquals(1, r.subchains.size());
        assertEquals(4, r.subchains.get(0).sessionIndexes.size());
    }
}
