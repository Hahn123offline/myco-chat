package com.dsh.v2;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.dsh.agent.LlmCaller;
import com.dsh.agent.l1.MergeGrouping;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨窗口合并：候选对（规则）→ 模型 yes/no → 贪心分组（冲突跳过）→ 父链。
 * 不再受 L0 chunk 限制；只合并同一天、≤24h 的链。
 */
public final class CrossWindowMerge {

    private static final int MAX_CANDIDATES = 40;
    private static final int MAX_INPUT_BODY = 200;

    public static final String JUDGE_SYSTEM = "" +
            "你判断两条记忆链是不是「同一件事的不同部分」。只输出 JSON。\n" +
            "A. 同一件事的不同阶段 → yes：例如早上遇到代理错误，晚上查怎么修。\n" +
            "B. 同一类活动的不同实例 → no：例如上午刷B站、晚上刷B站，是两件事。\n" +
            "C. 有共同元素但无关 → no：例如都在聊微信，但一件扫码报错、一件看群消息。\n" +
            "只有 A 是 yes。默认 no；判断不了就 no。\n" +
            "输出：{\"yes\":true/false,\"why\":\"一句话理由\"}";

    private CrossWindowMerge() { }

    public static final class Result {
        public final boolean ok;
        public final String report;
        Result(boolean ok, String report) { this.ok = ok; this.report = report; }
    }

    public static Result run(Context ctx, LlmCaller judge, LlmCaller bodyCaller) {
        StringBuilder report = new StringBuilder();
        try {
            ChainStore chains = new ChainStore(ctx);
            chains.clearSupersededParentLinks();
            List<ChainStore.ChainRow> rows = loadActive(chains);
            if (rows.size() < 2) return new Result(true, "active chains <2");
            TraceStore traces = new TraceStore(ctx);
            Map<Long, String> pkgById = new HashMap<>();
            for (ChainStore.ChainRow r : rows) {
                pkgById.put(r.id, traces.pkgMode(parseIds(r.blockIds)));
            }

            List<MergeCandidates.Item> items = new ArrayList<>();
            for (ChainStore.ChainRow r : rows) {
                items.add(new MergeCandidates.Item(r.id, r.title, r.body, r.t0, r.t1,
                        pkgById.get(r.id), r.level, r.parentId, ""));
            }
            List<MergeCandidates.Candidate> cands = MergeCandidates.generate(items);
            cands.sort(Comparator.comparingInt((MergeCandidates.Candidate c) -> c.score).reversed());
            if (cands.size() > MAX_CANDIDATES) cands = cands.subList(0, MAX_CANDIDATES);
            report.append("candidates=").append(cands.size()).append('\n');
            if (cands.isEmpty()) return new Result(true, report.toString());

            Map<Long, ChainStore.ChainRow> byId = new HashMap<>();
            for (ChainStore.ChainRow r : rows) byId.put(r.id, r);

            // 1) 模型逐对判断；yes 边保留分数；判过的同 body 指纹直接复用
            List<Edge> yes = new ArrayList<>();
            int judged = 0, cached = 0, autoYes = 0;
            for (MergeCandidates.Candidate c : cands) {
                ChainStore.ChainRow a = byId.get(c.a), b = byId.get(c.b);
                if (a == null || b == null) continue;
                String aSha = ChainStore.sha1(a.body);
                String bSha = ChainStore.sha1(b.body);
                if (c.why.contains("error_code=")) {
                    // 错误码是确定性信号：直接 yes，不调模型
                    autoYes++;
                    chains.putJudgment((int) a.id, (int) b.id, "yes", aSha, bSha);
                    yes.add(new Edge(a.id, b.id, c.score, c.why));
                    report.append(" auto-yes ").append(a.id).append('-').append(b.id)
                            .append(" ").append(c.why).append('\n');
                    continue;
                }
                ChainStore.Judgment j = chains.judgment((int) a.id, (int) b.id);
                if (j != null && aSha.equals(j.aSha) && bSha.equals(j.bSha)) {
                    cached++;
                    boolean ok = "yes".equals(j.verdict);
                    report.append(" cache ").append(a.id).append('-').append(b.id)
                            .append(" verdict=").append(j.verdict).append('\n');
                    if (ok) yes.add(new Edge(a.id, b.id, c.score, c.why));
                    continue;
                }
                boolean ok = judgePair(judge, a, b, c);
                judged++;
                chains.putJudgment((int) a.id, (int) b.id, ok ? "yes" : "no", aSha, bSha);
                report.append(" pair ").append(a.id).append('-').append(b.id)
                        .append(" score=").append(c.score)
                        .append(" yes=").append(ok)
                        .append(" why=").append(c.why).append('\n');
                if (ok) yes.add(new Edge(a.id, b.id, c.score, c.why));
            }
            yes.sort(Comparator.comparingInt((Edge e) -> e.score).reversed());

            // 2) 贪心分组：一个 child 只能进一个 group；冲突跳过
            Map<Long, List<Long>> groups = new LinkedHashMap<>();
            Map<Long, Long> owner = new HashMap<>();
            for (Edge e : yes) {
                Long ga = owner.get(e.a), gb = owner.get(e.b);
                if (ga == null && gb == null) {
                    List<Long> g = new ArrayList<>();
                    g.add(e.a); g.add(e.b);
                    groups.put(e.a, g);
                    owner.put(e.a, e.a); owner.put(e.b, e.a);
                } else if (ga != null && gb == null) {
                    addToGroup(groups, owner, ga, e.b);
                } else if (ga == null && gb != null) {
                    addToGroup(groups, owner, gb, e.a);
                }
                // 两端已有组（无论同/不同）都跳过，避免一子两父
            }

            // 3) 建父链（组内已有 parent_id 则跳过整组）
            int made = 0, skipped = 0;
            for (Map.Entry<Long, List<Long>> en : groups.entrySet()) {
                List<ChainStore.ChainRow> chosen = new ArrayList<>();
                boolean conflict = false;
                long oldParent = -1;
                boolean mixedParents = false;
                for (Long id : en.getValue()) {
                    ChainStore.ChainRow r = byId.get(id);
                    if (r == null) continue;
                    if (r.parentId > 0) {
                        conflict = true;
                        if (oldParent < 0) oldParent = r.parentId;
                        else if (oldParent != r.parentId) mixedParents = true;
                    }
                    chosen.add(r);
                }
                String childIds = "";
                String stableBody = null;
                if (chosen.size() >= 2) {
                    Collections.sort(chosen, Comparator.comparingLong(r -> r.id));
                    childIds = ids(chosen);
                    long existing = chains.idByChildIds(childIds);
                    if (existing <= 0) {
                        existing = chains.idByDedup(
                                com.dsh.agent.ChainKey.ofBlockCsv(childIds));
                    }
                    if (existing > 0) {
                        ChainStore.Info info = chains.info(existing);
                        if (info != null && "done".equals(info.status)
                                && !info.body.trim().isEmpty()) {
                            for (ChainStore.ChainRow r : chosen) chains.setParent(r.id, existing);
                            made++;
                            Log.i(MergePass.TAG, "merge parent: reuse #" + existing
                                    + " for [" + childIds + "]");
                            report.append(" reuse parent#").append(existing)
                                    .append(" children=").append(chosen.size()).append('\n');
                            continue;
                        }
                        // 兜底：即使行状态不是 done，也复用历史 body，保证记忆文本稳定
                        if (info != null && !info.body.trim().isEmpty()) {
                            stableBody = info.body;
                            Log.i(MergePass.TAG, "merge parent: stable body from #"
                                    + existing + " for [" + childIds + "]");
                        }
                    }
                }
                if (conflict) {
                    // 子链集合变了：旧父链 superseded，并清子链 parent_id，允许重挂
                    if (oldParent > 0 && !mixedParents) {
                        chains.supersedeParentAndClear(oldParent);
                        report.append(" superseded old parent#").append(oldParent).append('\n');
                        conflict = false;
                    }
                }
                if (conflict || chosen.size() < 2) {
                    skipped++;
                    report.append(" skip group parent-conflict size=")
                            .append(chosen.size()).append('\n');
                    continue;
                }
                List<Integer> idx = new ArrayList<>();
                for (int i = 0; i < chosen.size(); i++) idx.add(i);
                MergeGrouping.Group g = new MergeGrouping.Group(idx, chosen.get(0).title,
                        "cross-window:" + yesReason(en.getValue(), yes));
                MergePass.BodyResult br = stableBody == null
                        ? MergePass.generateBody(bodyCaller, chosen, g, 0, "")
                        : new MergePass.BodyResult(stableBody, "stable-copy");
                if (br.body == null) {
                    skipped++;
                    report.append(" skip body ").append(br.reason).append('\n');
                    continue;
                }
                String blockIds = unionBlocks(chosen);
                long t0 = Long.MAX_VALUE, t1 = Long.MIN_VALUE;
                for (ChainStore.ChainRow r : chosen) {
                    t0 = Math.min(t0, r.t0);
                    t1 = Math.max(t1, r.t1);
                }
                long pid = chains.insertParent("cross", g.title, childIds, blockIds,
                        t0, t1, g.why);
                if (pid < 0) {
                    pid = chains.idByDedup(com.dsh.agent.ChainKey.ofBlockCsv(childIds));
                    if (pid <= 0) {
                        skipped++;
                        report.append(" skip parent exists\n");
                        continue;
                    }
                }
                chains.updateParentBody(pid, br.body, keysOf(chosen),
                        ChainOutcome.inferFromBody(br.body).dbValue);
                for (ChainStore.ChainRow r : chosen) chains.setParent(r.id, pid);
                made++;
                Log.i(MergePass.TAG, "merge parent: create #" + pid
                        + " for [" + childIds + "] len=" + br.body.length());
                report.append(" parent#").append(pid).append(" children=")
                        .append(chosen.size()).append(" len=").append(br.body.length())
                        .append(" why=").append(g.why).append('\n');
            }
            report.append("parents=").append(made).append(" skipped=").append(skipped)
                    .append(" candidates=").append(cands.size())
                    .append(" judged=").append(judged)
                    .append(" cached=").append(cached)
                    .append(" auto_yes=").append(autoYes)
                    .append(" skip_rate=")
                    .append(cands.isEmpty() ? 0 : (100 * cached / cands.size())).append("%\n");
            return new Result(true, report.toString());
        } catch (Throwable t) {
            Log.e(MergePass.TAG, "cross-window merge failed", t);
            return new Result(false, "cross-window failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
        }
    }

    private static final class Edge {
        final long a, b;
        final int score;
        final String ruleWhy;
        Edge(long a, long b, int score, String why) {
            this.a = a; this.b = b; this.score = score; this.ruleWhy = why;
        }
    }

    private static void addToGroup(Map<Long, List<Long>> groups, Map<Long, Long> owner,
                                   long groupKey, long child) {
        List<Long> g = groups.get(groupKey);
        if (g == null) return;
        g.add(child);
        owner.put(child, groupKey);
    }

    private static List<ChainStore.ChainRow> loadActive(ChainStore chains) {
        List<ChainStore.ChainRow> out = new ArrayList<>();
        Cursor c = chains.all();
        while (c.moveToNext()) {
            int level = c.getInt(c.getColumnIndex("level"));
            String status = c.getString(c.getColumnIndex("status"));
            if (level > 1 || !"done".equals(status)) continue;
            out.add(new ChainStore.ChainRow(
                    c.getLong(c.getColumnIndex("id")),
                    c.getString(c.getColumnIndex("title")),
                    c.getString(c.getColumnIndex("body")),
                    c.getLong(c.getColumnIndex("t0")),
                    c.getLong(c.getColumnIndex("t1")),
                    c.getInt(c.getColumnIndex("n_blocks")),
                    c.getString(c.getColumnIndex("block_ids")),
                    c.getString(c.getColumnIndex("key_block_ids")),
                    c.getInt(c.getColumnIndex("parent_id")), level));
        }
        c.close();
        return out;
    }

    private static boolean judgePair(LlmCaller judge, ChainStore.ChainRow a,
                                     ChainStore.ChainRow b,
                                     MergeCandidates.Candidate cand) {
        if (judge == null) return false;
        try {
            long gapMin = Math.abs(a.t0 - b.t0) / 60_000L;
            String user = "候选证据：" + cand.why + "；相隔约 " + gapMin + " 分钟\n\n"
                    + "链A（" + fmt(a.t0) + "）标题：" + a.title + "\n正文："
                    + cut(a.body) + "\n\n"
                    + "链B（" + fmt(b.t0) + "）标题：" + b.title + "\n正文："
                    + cut(b.body) + "\n\n"
                    + "只输出 JSON：{\"yes\":true/false,\"why\":\"...\"}";
            ModelResponse r = judge.call(ModelRequest.text(JUDGE_SYSTEM, user)
                    .withProvenance(java.util.Collections.emptyList(), com.dsh.egress.EgressStage.DERIVED).withThinking("disabled"));
            if (r == null || !r.isOk() || r.text == null) return false;
            String t = r.text.toLowerCase();
            return t.contains("\"yes\":true") || t.contains("\"yes\": true")
                    || t.trim().startsWith("yes");
        } catch (Throwable t) {
            return false;
        }
    }

    private static String fmt(long ms) {
        long min = (ms / 60_000) % 1440;
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private static List<Long> parseIds(String csv) {
        List<Long> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            try { out.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
        }
        return out;
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() <= MAX_INPUT_BODY ? s : s.substring(0, MAX_INPUT_BODY);
    }

    private static String ids(List<ChainStore.ChainRow> rows) {
        StringBuilder b = new StringBuilder();
        for (ChainStore.ChainRow r : rows) {
            if (b.length() > 0) b.append(',');
            b.append(r.id);
        }
        return b.toString();
    }

    private static String unionBlocks(List<ChainStore.ChainRow> rows) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (ChainStore.ChainRow r : rows) {
            if (r.blockIds == null) continue;
            for (String x : r.blockIds.split(",")) if (!x.trim().isEmpty()) s.add(x.trim());
        }
        return String.join(",", s);
    }

    private static String keysOf(List<ChainStore.ChainRow> rows) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (ChainStore.ChainRow r : rows) {
            if (r.keyBlockIds == null) continue;
            for (String x : r.keyBlockIds.split(",")) if (!x.trim().isEmpty()) s.add(x.trim());
        }
        return String.join(",", s);
    }

    private static String yesReason(List<Long> group, List<Edge> yes) {
        StringBuilder b = new StringBuilder();
        for (Edge e : yes) {
            if (group.contains(e.a) && group.contains(e.b)) {
                if (b.length() > 0) b.append('|');
                b.append(e.ruleWhy);
            }
        }
        return b.toString();
    }
}
