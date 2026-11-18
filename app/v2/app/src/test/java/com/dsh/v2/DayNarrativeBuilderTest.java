package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DayNarrativeBuilderTest {

    private static String pad(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('叙');
        return b.toString();
    }

    private static final long BASE;

    static {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(2026, java.util.Calendar.NOVEMBER, 18, 0, 0, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        BASE = c.getTimeInMillis();
    }

    private static List<DayNarrativeBuilder.Event> events() {
        return Arrays.asList(
                new DayNarrativeBuilder.Event(6, "排查代理连接失败", "body6",
                        BASE + 10 * 3600_000L, BASE + 11 * 3600_000L, 3, "in_progress"),
                new DayNarrativeBuilder.Event(96, "排查微信扫码代理错误", "body96",
                        BASE + 18 * 3600_000L, BASE + 19 * 3600_000L, 4, "in_progress"));
    }

    @Test public void 合法索引通过C1到C4() {
        String json = "{\"narrative\":\"" + pad(320) + "\","
                + "\"segments\":[{\"text\":\"上午\",\"event_ids\":[6],\"time_hint\":\"10:00-11:00\"},"
                + "{\"text\":\"晚上\",\"event_ids\":[96],\"time_hint\":\"18:00-19:00\"}],"
                + "\"open_threads\":[{\"text\":\"代理\",\"event_ids\":[6,96]}],"
                + "\"gaps\":[],\"unmentioned_event_ids\":[]}";
        DayNarrativeBuilder.Parsed p = DayNarrativeBuilder.parse(json);
        assertTrue(DayNarrativeBuilder.validate(p, events(), new ArrayList<>()).isEmpty());
    }

    @Test public void 不足两条主线被拒绝但覆盖率和短长度只警告() {
        String json = "{\"narrative\":\"" + pad(320) + "\","
                + "\"segments\":[{\"text\":\"一条\",\"event_ids\":[6],\"time_hint\":\"10:00-10:10\"}],"
                + "\"open_threads\":[],\"gaps\":[],\"unmentioned_event_ids\":[]}";
        List<String> errs = DayNarrativeBuilder.validate(
                DayNarrativeBuilder.parse(json), events(), new ArrayList<>());
        assertTrue(errs.toString().contains("C1"));
        String shortJson = "{\"narrative\":\"短\",\"segments\":[{\"text\":\"上午\",\"event_ids\":[6]},"
                + "{\"text\":\"晚上\",\"event_ids\":[96]}],\"open_threads\":[],"
                + "\"gaps\":[],\"unmentioned_event_ids\":[]}";
        DayNarrativeBuilder.Parsed shortP = DayNarrativeBuilder.parse(shortJson);
        assertTrue(DayNarrativeBuilder.validate(shortP, events(), new ArrayList<>()).isEmpty());
        assertTrue(DayNarrativeBuilder.narrativeWarning(shortP.narrative).startsWith("short="));
        assertTrue(DayNarrativeBuilder.coverageWarning(shortP, events()).contains("unmentioned=0/2"));
    }

    @Test public void 禁止表述被拒绝() {
        String json = "{\"narrative\":\"" + pad(320) + "尚未完成" + "\","
                + "\"segments\":[{\"text\":\"上午\",\"event_ids\":[6]},"
                + "{\"text\":\"晚上\",\"event_ids\":[96]}],"
                + "\"open_threads\":[],\"gaps\":[],\"unmentioned_event_ids\":[]}";
        assertTrue(DayNarrativeBuilder.validate(
                DayNarrativeBuilder.parse(json), events(), new ArrayList<>())
                .toString().contains("C4"));
    }
    @Test public void memory_ids字段恒存在_没有聊天记忆也为空() {
        String json = "{\"narrative\":\"" + pad(400) + "\",\"mainlines\":["
                + "{\"title\":\"a\",\"text\":\"b\",\"event_ids\":[6],\"time_hint\":\"10:00-11:00\"},"
                + "{\"title\":\"c\",\"text\":\"d\",\"event_ids\":[96],\"time_hint\":\"18:00-19:00\"}],"
                + "\"open_threads\":[{\"text\":\"x\",\"event_ids\":[6]}],"
                + "\"presence_notes\":[],\"gaps\":[]}";
        DayNarrativeBuilder.Parsed p = DayNarrativeBuilder.parse(json);
        assertTrue(p.mainlines.get(0).memoryIds.isEmpty());
        assertTrue(p.threads.get(0).memoryIds.isEmpty());
    }

    @Test public void n_blocks大于等于8必须被引用() {
        List<DayNarrativeBuilder.Event> es = new ArrayList<>(events());
        es.add(new DayNarrativeBuilder.Event(42, "零杯大赛", "big",
                BASE + 12 * 3600_000L, BASE + 13 * 3600_000L, 34, "unknown"));
        String json = "{\"narrative\":\"" + pad(400) + "\",\"mainlines\":["
                + "{\"title\":\"a\",\"text\":\"b\",\"event_ids\":[6,96],\"time_hint\":\"10:00-19:00\"}],"
                + "\"open_threads\":[],\"gaps\":[]}";
        DayNarrativeBuilder.Parsed p = DayNarrativeBuilder.parse(json);
        assertTrue(DayNarrativeBuilder.validate(p, es, new ArrayList<>())
                .stream().anyMatch(x -> x.startsWith("C5:")));
    }

    @Test public void C2跨窗口hint应通过_窄hint才矛盾() {
        List<DayNarrativeBuilder.Event> es = events();
        String wide = "{\"narrative\":\"" + pad(400) + "\",\"mainlines\":["
                + "{\"title\":\"a\",\"text\":\"b\",\"event_ids\":[6,96],\"time_hint\":\"09:30-19:30\"},"
                + "{\"title\":\"c\",\"text\":\"d\",\"event_ids\":[6],\"time_hint\":\"10:00-11:00\"}],"
                + "\"open_threads\":[],\"gaps\":[]}";
        assertTrue(DayNarrativeBuilder.validate(DayNarrativeBuilder.parse(wide), es,
                new ArrayList<>()).stream().noneMatch(x -> x.startsWith("C2:")));
        String narrow = wide.replace("09:30-19:30", "10:00-11:00");
        assertTrue(DayNarrativeBuilder.validate(DayNarrativeBuilder.parse(narrow), es,
                new ArrayList<>()).stream().anyMatch(x -> x.startsWith("C2:")));
    }

    @Test public void 事件gap大于20分钟在输入里附注() {
        DayNarrativeBuilder.Event e = new DayNarrativeBuilder.Event(1, "t", "b",
                BASE, BASE + 60 * 60_000L, 5, "unknown");
        e.coveredMs = 10 * 60_000L;
        String prompt = DayNarrativeBuilder.userPrompt("d", java.util.Collections.singletonList(e),
                new ArrayList<>());
        assertTrue(prompt.contains("分钟无记录"));
    }

}
