package com.dsh.agent.l1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.dsh.agent.ScriptedCaller;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MergeGroupingTest {

    private static List<MergeGrouping.Item> items(int n) {
        List<MergeGrouping.Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new MergeGrouping.Item(i, "子链" + i, "这是子链" + i + "的客观描述内容。",
                    1000L * i, 1000L * i + 500, 2));
        }
        return out;
    }

    @Test public void 修代理三条合成一组() {
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText(
                "{\"merge_groups\":[{\"children\":[0,1,2],\"title\":\"排查并修复网页代理\","
                        + "\"why\":\"先后处理代理报错、WLAN 和 Clash 节点，同一目的\"},"
                        + "{\"children\":[3]}]}"));
        MergeGrouping.Result r = MergeGrouping.group(items(4), caller);
        assertEquals(1, r.groups.size());
        assertEquals(3, r.groups.get(0).children.size());
        assertEquals("排查并修复网页代理", r.groups.get(0).title);
        assertEquals("disabled", caller.requests.get(0).thinking);
        assertEquals(0, caller.requests.get(0).maxTokens);
    }

    @Test public void 部分覆盖只合确定组_其余保持独立() {
        MergeGrouping.Result r = MergeGrouping.parse(
                "{\"merge_groups\":[{\"children\":[0,1],\"title\":\"A\",\"why\":\"x\"}]}", 4);
        assertEquals(1, r.groups.size());
        assertEquals(2, r.groups.get(0).children.size());
    }

    @Test public void 缺why不合并() {
        MergeGrouping.Result r = MergeGrouping.parse(
                "{\"merge_groups\":[{\"children\":[0,1],\"title\":\"A\"}]}", 2);
        assertTrue(r.empty());
    }

    @Test public void 不同目的不合并() {
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText(
                "{\"merge_groups\":[{\"children\":[0]},{\"children\":[1]}]}"));
        MergeGrouping.Result r = MergeGrouping.group(items(2), caller);
        assertTrue(r.empty());
    }

    @Test public void 数组格式兼容且单组不生成父链() {
        MergeGrouping.Result r = MergeGrouping.parse(
                "{\"merge_groups\":[[0,1],[2]]}", 3);
        assertEquals(1, r.groups.size());
        assertEquals(2, r.groups.get(0).children.size());
    }
}
