package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.agent.l1.MergeGrouping;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParentBodySpecTest {

    private static String body(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('事');
        return b.toString();
    }

    @Test public void 长度400到800合规() {
        assertEquals("", ParentBodySpec.validate(body(400)));
        assertEquals("", ParentBodySpec.validate(body(800)));
        // 短 body 接受，由 DB 层标 short_body；超长仍报太长（由调用方重试/保留）
        assertEquals("", ParentBodySpec.validate(body(399)));
        assertTrue(ParentBodySpec.validate(body(801)).contains("太长"));
    }

    @Test public void 禁止继承范围断言() {
        String clean = body(500);
        String bad = clean.substring(0, 450) + "全程停留在 com.deepseek.chat" + body(20);
        assertTrue(ParentBodySpec.validate(bad).contains("禁止"));
        assertTrue(ParentBodySpec.validate(clean.substring(0, 450) + "未看到修改动作")
                .contains("禁止"));
        assertTrue(ParentBodySpec.validate(clean.substring(0, 450) + "整个过程都在设置")
                .contains("禁止"));
        assertTrue(ParentBodySpec.validate(clean.substring(0, 450) + "问题是否解决无从判断")
                .contains("禁止"));
        assertTrue(ParentBodySpec.validate(clean.substring(0, 450) + "最终解决了问题")
                .contains("禁止"));
    }

    @Test public void 允许父链层面的判断() {
        String s = body(360) + "他先遇到代理报错，然后去设置查 WLAN，最后看了 Clash，"
                + "但没有看到实际修改的动作。这件事没有明确的结束——最后停在 Clash 页面。";
        assertEquals("", ParentBodySpec.validate(s));
    }

    @Test public void 允许限定语的未看到() {
        String s = body(380) + "截至记录结束，尚未看到修改代理的动作，最后停在 Clash 页面。";
        assertEquals("", ParentBodySpec.validate(s));
        assertFalse(ParentBodySpec.normalize(s).contains("未看到"));
        assertTrue(ParentBodySpec.normalize(s).contains("尚未观察到"));
    }

    @Test public void prompt包含硬要求且只传文本() {
        List<MergeGrouping.Item> items = new ArrayList<>();
        items.add(new MergeGrouping.Item(0, "排查代理", body(100), 0, 60_000, 6));
        items.add(new MergeGrouping.Item(1, "看WLAN", body(100), 60_000, 120_000, 3));
        MergeGrouping.Group g = MergeGrouping.parse(
                "{\"merge_groups\":[{\"children\":[0,1],\"title\":\"修代理\",\"why\":\"同一目的\"}]}",
                2).groups.get(0);
        String p = ParentBodySpec.userPrompt(items, g);
        assertTrue(p.contains("400–800"));
        assertTrue(p.contains("不要按时间逐条重放"));
        assertTrue(p.contains("全程停留在"));
        assertTrue(p.contains("排查代理"));
    }

    @Test public void key从子链key中挑且不超过5个() {
        String keys = ParentBodySpec.pickKeyIds(Arrays.asList("[1,2,3]", "4,5", "6"));
        assertEquals("1,4,6,2,3", keys);
        assertEquals(5, keys.split(",").length);
    }
}
