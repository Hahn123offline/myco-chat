package com.dsh.v2;

import com.dsh.models.Json;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** L3 日叙事的输入拼装、JSON 解析与 C1–C4 校验（纯逻辑，可单测）。 */
public final class DayNarrativeBuilder {

    public static final String SYSTEM = "" +
            "你在为一天写一份带指针的索引，不是屏幕清单。\n" +
            "先想：今天有哪几件**有目的的事**（2–5 件），每件是怎么开始的、做了什么、到什么程度。\n" +
            "然后按主线组织，不要按时间顺序逐个列举应用和界面。\n" +
            "规则：\n" +
            "1. narrative 600–1200 字，第一人称见证者叙述，讲这一天的主线；不许编造情绪、因果、关系。\n" +
            "2. **禁止清单体**：不要写「XX界面出现」「随后打开XX」这种按时间罗列；要写「你在做什么、为了什么」。\n" +
            "3. 可以跳过零碎事件不提；剩下的事件按时间可查，不需要面面俱到。\n" +
            "4. 只写输入里有的事；中间没有记录就如实写「中间有一段没有记录」。\n" +
            "5. 禁止「尚未」「没做完」「无从判断」「最终解决了」（除非 outcome=resolved）。\n" +
            "5b. 人称硬规则：默认只能「翻记录看到…」「今天看到你在…」；材料 evidence 含 chat/manual 才能说「那会儿我们在聊这个」「你当时特意让我记住这个」；任何情况都不许说「我做了 X」，你是见证者不是行动者。\n" +
            "6. mainlines 2–5 条，每条 title + text + event_ids + time_hint；\n" +
            "   一条 mainline 的事件可以跨时段、不连续——这是索引的关键能力。\n" +
            "   如果多个事件共享同一个错误码（如 ERR_PROXY_CONNECTION_FAILED）、同一症状或同一目标，\n" +
            "   即使相隔数小时，也必须放进同一条 mainline，而不是按时间段各提一次。\n" +
            "7. gaps 只能抄输入给的缺口；open_threads 0–3 条，每条带 event_ids 和 memory_ids。\n" +
            "7b. **n_blocks >= 8 的事件是当天注意力重心，必须被某条主线的 event_ids 引用**；确实没写进主线时放进 unmentioned_event_ids 并说明原因。不要因此逐条罗列所有事件。\n" +
            "8. 未提及事件只在 unmentioned_event_ids 里记录，不要求全覆盖。\n" +
            "9. 输出必须含 presence_notes（今天哪些事我在场；没有就给空数组）。\n" +
            "只输出 JSON，不要 markdown：\n" +
            "{\"narrative\":\"...\",\"mainlines\":[{\"title\":\"...\",\"text\":\"...\"," +
            "\"event_ids\":[6,96,105],\"time_hint\":\"10:14-19:18\"}]," +
            "\"open_threads\":[{\"text\":\"...\",\"event_ids\":[1],\"memory_ids\":[]}]," +
            "\"presence_notes\":[{\"text\":\"那会儿我们在聊...\",\"memory_ids\":[1620]}]," +
            "\"gaps\":[\"13:07–16:52 无记录\"],\"unmentioned_event_ids\":[2]}";

    public static final class Event {
        public final long id;
        public final String title, body, outcome;
        public final long t0, t1;
        public final int blocks;
        /** 块区间并集；0 = 未知。 */
        public long coveredMs;
        public Event(long id, String title, String body, long t0, long t1, int blocks,
                     String outcome) {
            this.id = id; this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.t0 = t0; this.t1 = t1; this.blocks = blocks;
            this.outcome = outcome == null ? "unknown" : outcome;
        }
    }

    public static final class Memory {
        public final long id;
        public final String text, evidence;
        public final long t0;
        public Memory(long id, String text, String evidence, long t0) {
            this.id = id; this.text = text == null ? "" : text;
            this.evidence = evidence == null ? "trace" : evidence; this.t0 = t0;
        }
    }

    public static final class Presence {
        public final String text;
        public final List<Long> memoryIds;
        public Presence(String text, List<Long> ids) {
            this.text = text == null ? "" : text;
            this.memoryIds = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
        }
    }

    public static final class Parsed {
        public final String narrative;
        public final List<Presence> presenceNotes = new ArrayList<>();
        public final List<DayNarrative.Segment> segments = new ArrayList<>();
        public final List<DayNarrative.Mainline> mainlines = new ArrayList<>();
        public final List<DayNarrative.Thread> threads = new ArrayList<>();
        public final List<String> gaps = new ArrayList<>();
        public final List<Long> unmentioned = new ArrayList<>();
        public boolean repaired;
        public int repairedAdded;
        Parsed(String narrative) { this.narrative = narrative == null ? "" : narrative; }
    }


    /**
     * 确定性兜底：只补 n_blocks>=8 的漏引，纯指针，不合成人称/情绪/因果。
     * 规则：title 固定「其他注意力」，text 只陈述数量，不描述内容。
     */
    public static Parsed repair(Parsed p, List<Event> events) {
        Set<Long> mentioned = new HashSet<>();
        for (DayNarrative.Mainline m : p.mainlines) mentioned.addAll(m.eventIds);
        List<Long> missing = new ArrayList<>();
        for (Event e : events) {
            if (e.blocks >= 8 && !mentioned.contains(e.id)) missing.add(e.id);
        }
        if (!missing.isEmpty()) {
            p.mainlines.add(new DayNarrative.Mainline("其他注意力",
                    "另有 " + missing.size() + " 段注意力落在下列事件上",
                    missing, new ArrayList<>(), ""));
            p.repaired = true;
            p.repairedAdded = missing.size();
        }
        return p;
    }

    private static final Pattern BANNED = Pattern.compile(
            "尚未|没做完|还没完成|无从判断|截至记录结束");
    private static final Pattern FINAL_CLAIM = Pattern.compile("最终解决了|已经解决|彻底解决");

    private DayNarrativeBuilder() { }

    public static String userPrompt(String day, List<Event> events, List<String> gaps) {
        return userPrompt(day, events, gaps, new ArrayList<>(), "");
    }

    public static String userPrompt(String day, List<Event> events, List<String> gaps,
                                    List<Memory> memories, String coverage) {
        StringBuilder b = new StringBuilder();
        b.append("日期：").append(day).append('\n');
        b.append("时间线状态：\n");
        for (String g : gaps) b.append("- ").append(g).append('\n');
        StringBuilder must = new StringBuilder();
        for (Event e : events) if (e.blocks >= 8) {
            if (must.length() > 0) must.append(',');
            must.append(e.id);
        }
        b.append("\n必须被某条主线 event_ids 引用的事件（n_blocks>=8）：").append(must).append('\n');
        b.append("\n事件（共 ").append(events.size()).append(" 条）：\n");
        java.util.List<Event> ordered = new java.util.ArrayList<>(events);
        ordered.sort((x, y) -> Integer.compare(y.blocks, x.blocks));
        for (Event e : ordered) {
            b.append("id=").append(e.id)
                    .append(" 时间=").append(fmt(e.t0)).append('-').append(fmt(e.t1))
                    .append(" 块数=").append(e.blocks)
                    .append(" span_min=").append((e.t1 - e.t0) / 60_000)
                    .append(" covered_min=").append(e.coveredMs / 60_000)
                    .append(" gap_min=").append(Math.max(0,
                            (e.t1 - e.t0 - Math.max(0, e.coveredMs)) / 60_000))
                    .append(" outcome=").append(e.outcome)
                    .append(" 标题=").append(e.title).append('\n')
                    .append(e.body);
            long span = e.t1 - e.t0;
            long gap = span - Math.max(0, e.coveredMs);
            if (e.coveredMs > 0 && gap > 20 * 60_000L) {
                b.append("\n（该事件时间跨度 ").append(span / 60_000)
                        .append(" 分钟，其中约 ").append(gap / 60_000)
                        .append(" 分钟无记录）");
            }
            b.append("\n\n");
        }
        b.append("\n聊天/手动记忆（namespace=chat/manual，当天）：")
                .append(memories == null ? 0 : memories.size()).append(" 条\n");
        if (memories != null) for (Memory m : memories) {
            b.append("memory_id=").append(m.id).append(" evidence=").append(m.evidence)
                    .append(" 时间=").append(fmt(m.t0)).append(" text=").append(m.text)
                    .append('\n');
        }
        if (coverage != null && !coverage.isEmpty()) b.append("\n覆盖率：").append(coverage).append('\n');
        return b.toString();
    }

    public static Parsed replaceNarrative(Parsed p, String narrative) {
        Parsed q = new Parsed(narrative);
        q.segments.addAll(p.segments);
        q.mainlines.addAll(p.mainlines);
        q.threads.addAll(p.threads);
        q.gaps.addAll(p.gaps);
        q.unmentioned.addAll(p.unmentioned);
        q.repaired = p.repaired;
        return q;
    }

    public static String narrativeWarning(String narrative) {
        int len = narrative == null ? 0 : narrative.trim().length();
        if (len > 600) return "over_length=" + len;
        if (len < 300) return "short=" + len;
        return "";
    }

    public static String narrativeError(String narrative) {
        int len = narrative == null ? 0 : narrative.trim().length();
        if (len < 300 || len > 800) return "narrative " + len + " 字不在 300–800";
        if (narrative != null && BANNED.matcher(narrative).find()) return "narrative 含禁止表述";
        return "";
    }

    public static String compressPrompt(String narrative) {
        return "下面这段日叙事索引的正文有 " + (narrative == null ? 0 : narrative.length())
                + " 字，超过 800。请只重写正文，压缩到 600–800 字，保留主线、缺口和范围限定；"
                + "不要截断，不要改 segments，不要输出 JSON，只输出压缩后的正文：\n\n"
                + (narrative == null ? "" : narrative);
    }

    public static Parsed parse(String json) {
        Map<String, Object> o = Json.asObject(Json.parse(extractJson(json)));
        if (o == null) throw new IllegalArgumentException("不是 JSON 对象");
        Parsed p = new Parsed(Json.string(o.get("narrative")));
        List<Object> mains = Json.asArray(o.get("mainlines"));
        if (mains != null) {
            for (Object x : mains) {
                Map<String, Object> m = Json.asObject(x);
                if (m == null) continue;
                p.mainlines.add(new DayNarrative.Mainline(Json.string(m.get("title")),
                        Json.string(m.get("text")), longs(m.get("event_ids")),
                        longs(m.get("memory_ids")), Json.string(m.get("time_hint"))));
            }
        }
        List<Object> segs = Json.asArray(o.get("segments"));
        if (segs != null) {
            for (Object x : segs) {
                Map<String, Object> m = Json.asObject(x);
                if (m == null) continue;
                p.segments.add(new DayNarrative.Segment(Json.string(m.get("text")),
                        longs(m.get("event_ids")), Json.string(m.get("time_hint"))));
            }
        }
        // 兼容旧输出/测试：没有 mainlines 时把 segments 当 mainlines
        if (p.mainlines.isEmpty()) {
            for (DayNarrative.Segment s : p.segments) {
                p.mainlines.add(new DayNarrative.Mainline("", s.text, s.eventIds,
                        new ArrayList<>(), s.timeHint));
            }
        }
        List<Object> ths = Json.asArray(o.get("open_threads"));
        if (ths != null) {
            for (Object x : ths) {
                Map<String, Object> m = Json.asObject(x);
                if (m == null) continue;
                p.threads.add(new DayNarrative.Thread(Json.string(m.get("text")),
                        longs(m.get("event_ids")), longs(m.get("memory_ids"))));
            }
        }
        List<Object> pns = Json.asArray(o.get("presence_notes"));
        if (pns != null) for (Object x : pns) {
            Map<String, Object> m = Json.asObject(x);
            if (m != null) p.presenceNotes.add(new Presence(Json.string(m.get("text")),
                    longs(m.get("memory_ids"))));
        }
        List<Object> gs = Json.asArray(o.get("gaps"));
        if (gs != null) for (Object x : gs) if (x instanceof String) p.gaps.add((String) x);
        p.unmentioned.addAll(longs(o.get("unmentioned_event_ids")));
        return p;
    }

    public static List<String> validate(Parsed p, List<Event> events, List<String> inputGaps) {
        return validate(p, events, inputGaps, false);
    }

    /** 返回硬校验失败原因；C3 覆盖率不在这里判失败。 */
    public static List<String> validate(Parsed p, List<Event> events, List<String> inputGaps,
                                        boolean hasChatMemory) {
        List<String> errs = new ArrayList<>();
        Set<Long> valid = new HashSet<>();
        for (Event e : events) valid.add(e.id);
        if (p.mainlines.size() < 2) errs.add("C1: mainlines < 2");
        for (DayNarrative.Mainline m : p.mainlines) {
            m.eventIds.removeIf(id -> !valid.contains(id));
            if (m.eventIds.isEmpty()) errs.add("C1: mainline 无有效 event_ids");
        }
        for (DayNarrative.Thread t : p.threads) {
            t.eventIds.removeIf(id -> !valid.contains(id));
            if (t.eventIds.isEmpty()) errs.add("C1: open_thread 无有效 event_ids");
        }
        for (String g : p.gaps) {
            if (!inputGaps.contains(g)) errs.add("C4: gaps 不在输入缺口里: " + g);
        }

        for (DayNarrative.Mainline m : p.mainlines) {
            if (!timeContradicts(m.eventIds, m.timeHint, events)) continue;
            errs.add("C2: time_hint 与事件时间矛盾 " + m.timeHint);
        }

        if (p.narrative != null && BANNED.matcher(p.narrative).find()) {
            errs.add("C4: narrative 含禁止表述");
        }
        Set<Long> mentioned = new HashSet<>();
        for (DayNarrative.Mainline m : p.mainlines) mentioned.addAll(m.eventIds);
        for (Event e : events) {
            if (e.blocks >= 8 && !mentioned.contains(e.id)) {
                errs.add("C5: n_blocks>=8 事件未引用 id=" + e.id);
            }
        }
        if (p.narrative != null && FINAL_CLAIM.matcher(p.narrative).find()) {
            boolean anyResolved = false;
            for (Event e : events) if ("resolved".equals(e.outcome)) anyResolved = true;
            if (!anyResolved) errs.add("C4: 无 resolved 事件却断言最终解决");
        }
        if (hasChatMemory && p.narrative != null) {
            if (!p.narrative.contains("我们在聊") || !p.narrative.contains("翻记录")) {
                errs.add("C6: 有 chat 记忆时 narrative 必须同时出现「我们在聊」「翻记录」");
            }
        }
        return errs;
    }

    /** C3 只做观测：未提及事件数量，不判失败。 */
    public static String coverageWarning(Parsed p, List<Event> events) {
        Set<Long> mentioned = new HashSet<>();
        for (DayNarrative.Mainline m : p.mainlines) mentioned.addAll(m.eventIds);
        int un = 0;
        for (Event e : events) if (!mentioned.contains(e.id)) un++;
        return "unmentioned=" + un + "/" + events.size();
    }

    private static boolean timeContradicts(List<Long> eventIds, String hint,
                                           List<Event> events) {
        String h = hint == null ? "" : hint.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,2}):(\\d{2})\\s*[-–]\\s*(\\d{1,2}):(\\d{2})").matcher(h);
        if (!m.find()) return false;
        int hs = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        int he = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
        // hint 必须覆盖所引事件的 [min(t0), max(t1)]，容差 ±30min；跨窗口是正常能力
        Integer minStart = null, maxEnd = null;
        for (Long id : eventIds) {
            Event e = null;
            for (Event x : events) if (x.id == id) { e = x; break; }
            if (e == null) continue;
            int s = minuteOfDay(e.t0), en = minuteOfDay(e.t1);
            if (minStart == null || s < minStart) minStart = s;
            if (maxEnd == null || en > maxEnd) maxEnd = en;
        }
        if (minStart == null) return false;
        return hs > minStart + 30 || he < maxEnd - 30;
    }

    private static boolean timeContradicts(DayNarrative.Segment s, List<Event> events) {
        return timeContradicts(s.eventIds, s.timeHint, events);
    }

    private static List<Long> longs(Object o) {
        List<Long> out = new ArrayList<>();
        List<Object> a = Json.asArray(o);
        if (a == null) return out;
        for (Object x : a) {
            if (x instanceof Number) out.add(((Number) x).longValue());
            else if (x instanceof String) {
                try { out.add(Long.parseLong((String) x)); } catch (Throwable ignored) { }
            }
        }
        return out;
    }

    private static String extractJson(String s) {
        if (s == null) return "{}";
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return a >= 0 && b > a ? s.substring(a, b + 1) : s;
    }

    static String fmt(long ms) {
        int min = minuteOfDay(ms);
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private static int minuteOfDay(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }
}
