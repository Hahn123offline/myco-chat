package com.dsh.pcharness;

import com.dsh.models.Capability;
import com.dsh.models.CostClass;
import com.dsh.models.Json;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.OpenAiCompatProvider;
import com.dsh.models.UrlConnectionTransport;
import com.dsh.egress.EgressGate;
import com.dsh.egress.EgressResult;
import com.dsh.egress.EgressStage;
import com.dsh.egress.EgressTier;
import com.dsh.guard.Guard;
import com.dsh.memory.Evidence;
import com.dsh.memory.Kind;
import com.dsh.memory.MemItem;
import com.dsh.memory.MemoryCodec;
import com.dsh.memory.MemoryPolicy;
import com.dsh.memory.Query;
import com.dsh.v2.DayNarrative;
import com.dsh.v2.DayNarrativeBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 桌面 harness：读 raw/v2/chains.db 跑 L3，写 day_narratives.db。 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usage();
        if ("day".equals(args[0])) day(args);
        else if ("recall".equals(args[0])) recall(args);
        else if ("migrate-db".equals(args[0])) migrateDb(args);
        else if ("chat".equals(args[0])) ChatConsole.run(args);
        else if ("dump".equals(args[0])) ChatConsole.dump(args);
        else if ("fidelity".equals(args[0])) Fidelity.run(args);
        else if ("migrate-memory".equals(args[0])) migrateMemory(args);
        else if ("rebuild-lines".equals(args[0])) OfflineProjection.run(args);
        else if ("pipeline".equals(args[0])) {
            System.err.println("pipeline 子命令尚未实现（L0→L1→L2 桌面路径下一阶段）");
            System.exit(2);
        } else usage();
    }

    static String readApiKey() {
        String env = System.getenv("DSH_API_KEY");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        String[] paths = {"E:\\11-15\\.secrets\\deepseek.key", ".secrets/deepseek.key",
                "../.secrets/deepseek.key"};
        for (String p : paths) {
            try {
                Path f = Paths.get(p);
                if (!java.nio.file.Files.isRegularFile(f)) continue;
                String s = new String(java.nio.file.Files.readAllBytes(f),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) return s;
            } catch (Throwable ignored) {
                // 不把任何文件内容/路径异常带进输出
            }
        }
        return "";
    }

    private static void usage() {
        System.err.println("usage: day --date 2026-11-17 [--db raw/v2/chains.db]"
                + " [--out raw/v2/day_narratives.db] [--base https://api.deepseek.com]"
                + " [--model deepseek-chat]"
                + "\n       migrate-memory [--trace raw/v2/trace.db] [--out raw/v2/memory.db]"
                + "\n       recall --db raw/v2/memory.db [--query 关键词] [--limit 8]"
                + " [--eval probes.json] [--day-db day_narratives.db]");
        System.exit(2);
    }

    static String workspacePath(String s) {
        Path p = Paths.get(s);
        if (p.isAbsolute()) return p.toString();
        // harness 的工作目录通常是 app/v2/pcharness 或 app/v2；相对路径按 <workspace> 解析
        return Paths.get("E:\\11-15").resolve(p).normalize().toString();
    }

    static String arg(String[] a, String key, String def) {
        for (int i = 0; i + 1 < a.length; i++) if (key.equals(a[i])) return a[i + 1];
        return def;
    }

    private static List<String> daySources(String traceDb, long from, long to) {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + traceDb);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT DISTINCT pkg FROM traces WHERE t0>=? AND t0<=? AND pkg IS NOT NULL")) {
            ps.setLong(1, from);
            ps.setLong(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String p = rs.getString(1);
                    if (p != null && !p.trim().isEmpty() && !out.contains(p)) out.add(p);
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static List<com.dsh.trace.GapFinder.Gap> dayGaps(
            String traceDb, long from, long to) {
        List<com.dsh.trace.GapFinder.Span> spans = new ArrayList<>();
        List<com.dsh.trace.GapFinder.Span> content = new ArrayList<>();
        List<com.dsh.trace.GapFinder.Known> known = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + traceDb)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t0,t1,text,shot_ref,source FROM traces WHERE t1>=? AND t0<=? ORDER BY t0")) {
                ps.setLong(1, from);
                ps.setLong(2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        spans.add(new com.dsh.trace.GapFinder.Span(
                                rs.getLong(1), rs.getLong(2)));
                        String text = rs.getString(3);
                        String shot = rs.getString(4);
                        boolean hasContent = (text != null && !text.trim().isEmpty())
                                || (shot != null && !shot.trim().isEmpty());
                        if (hasContent) {
                            content.add(new com.dsh.trace.GapFinder.Span(
                                    rs.getLong(1), rs.getLong(2)));
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t0,t1,reason FROM trace_gaps WHERE t0<=? AND (t1 IS NULL OR t1>=?)")) {
                ps.setLong(1, to);
                ps.setLong(2, from);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) known.add(new com.dsh.trace.GapFinder.Known(
                            rs.getLong(1), rs.getLong(2), rs.getString(3)));
                }
            }
        } catch (Throwable ignored) { }
        return com.dsh.trace.GapFinder.find(spans, content, known, from, to);
    }


    /** 离线旧数据兼容：evidence.db 不存在时从 trace.db 复制，不改原文件。 */
    private static String ensureEvidence(String p) {
        java.io.File f = new java.io.File(p);
        if (f.exists()) return p;
        String alt = p.endsWith("evidence.db")
                ? p.substring(0, p.length() - "evidence.db".length()) + "trace.db" : "";
        if (!alt.isEmpty()) {
            java.io.File old = new java.io.File(alt);
            if (old.exists()) {
                try { java.nio.file.Files.copy(old.toPath(), f.toPath()); } catch (Throwable ignored) { }
            }
        }
        return p;
    }


    private static void migrateDb(String[] args) throws Exception {
        String mem = workspacePath(arg(args, "--mem", "raw/v2/memory.db"));
        String chains = workspacePath(arg(args, "--chains", "raw/v2/chains.db"));
        String trace = workspacePath(arg(args, "--trace", "raw/v2/trace.db"));
        Class.forName("org.sqlite.JDBC");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + mem);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS chains(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "l0_index INTEGER,title TEXT,session_ids TEXT,block_ids TEXT,t0 INTEGER,t1 INTEGER,"
                    + "n_blocks INTEGER,status TEXT,body TEXT,key_block_ids TEXT,provisional INTEGER DEFAULT 0,"
                    + "created_at INTEGER,dedup_key TEXT,chunk_key TEXT,parent_id INTEGER DEFAULT 0,"
                    + "child_ids TEXT,level INTEGER DEFAULT 0,merge_why TEXT,text_source TEXT,"
                    + "outcome TEXT DEFAULT 'unknown',gap_declared INTEGER DEFAULT 1,"
                    + "over_length INTEGER DEFAULT 0,short_body INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS l0_chunks(chunk_key TEXT PRIMARY KEY,t0 INTEGER,"
                    + "t1 INTEGER,block_ids TEXT,status TEXT,updated_at INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS merge_judgments(a_id INTEGER,b_id INTEGER,"
                    + "verdict TEXT,a_body_sha1 TEXT,b_body_sha1 TEXT,at INTEGER,PRIMARY KEY(a_id,b_id))");
            st.execute("CREATE TABLE IF NOT EXISTS pipeline_runs(id TEXT PRIMARY KEY,status TEXT,"
                    + "started_at INTEGER,finished_at INTEGER,hours INTEGER,block_from INTEGER,"
                    + "block_to INTEGER,l0_chunks INTEGER,chains INTEGER,bodies INTEGER,"
                    + "prompt_tokens INTEGER,completion_tokens INTEGER,cache_hit INTEGER,error TEXT)");
            st.execute("ATTACH DATABASE '" + chains.replace("'", "''") + "' AS src");
            for (String t : new String[]{"chains", "l0_chunks", "merge_judgments", "pipeline_runs"}) {
                int s = 0, d = 0;
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM src." + t)) {
                    if (rs.next()) s = rs.getInt(1);
                } catch (Throwable ignored) { continue; }
                st.executeUpdate("INSERT OR IGNORE INTO main." + t + " SELECT * FROM src." + t);
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM main." + t)) {
                    if (rs.next()) d = rs.getInt(1);
                }
                System.out.println("migrate " + t + " src=" + s + " dst=" + d);
                if (s != d) throw new IllegalStateException(t + " 行数不一致 " + s + "/" + d);
            }
            st.execute("DETACH DATABASE src");
        }
        java.io.File old = new java.io.File(trace);
        java.io.File ev = new java.io.File(trace.replace("trace.db", "evidence.db"));
        if (old.exists() && !ev.exists()) old.renameTo(ev);
        System.out.println("migrate-db done mem=" + mem
                + " evidence=" + ev.getAbsolutePath());
    }

    private static void recall(String[] args) throws Exception {
        String db = workspacePath(arg(args, "--db", "raw/v2/memory.db"));
        JdbcMemorySource source = new JdbcMemorySource(db);
        String key = readApiKey();
        String base = arg(args, "--base", "https://api.deepseek.com");
        String model = arg(args, "--model", "deepseek-chat");
        com.dsh.models.OpenAiCompatProvider provider = key.isEmpty() ? null
                : new com.dsh.models.OpenAiCompatProvider("deepseek", base, key, model,
                        180_000, Collections.singleton(Capability.JSON), 128_000,
                        CostClass.CHEAP, new UrlConnectionTransport());
        String eval = arg(args, "--eval", null);
        if (eval != null) {
            evalRecall(source, workspacePath(eval), provider);
            return;
        }
        com.dsh.memory.RecallQuery q = new com.dsh.memory.RecallQuery()
                .query(arg(args, "--query", ""))
                .from(arg(args, "--from", ""))
                .to(arg(args, "--to", ""))
                .pkg(arg(args, "--pkg", ""))
                .level(arg(args, "--level", "block"))
                .limit(Integer.parseInt(arg(args, "--limit", "8")));
        if ("day".equals(q.level)) {
            com.dsh.memory.DayRecall d = com.dsh.memory.RecallCore.day(source,
                    q.from == null || q.from.isEmpty() ? null : q.from);
            System.out.println(com.dsh.memory.RecallCore.renderDay(d));
        } else {
            System.out.println(com.dsh.memory.RecallCore.render(
                    com.dsh.memory.RecallCore.recall(source, q)));
        }
    }

    private static void evalRecall(JdbcMemorySource source, String path,
                                   com.dsh.models.ModelProvider provider) throws Exception {
        String text = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> root = Json.asObject(Json.parse(text));
        List<Object> families = Json.asArray(root == null ? null : root.get("families"));
        ModelMeter.reset();
        System.out.println("family probes R@1 R@3 R@5 pointer_ok neg_fp");
        if (families == null) { System.out.println("no families"); return; }
        int totalD = 0, pointerOk = 0, negFp = 0, appOnlyLeak = 0;
        Map<Long, com.dsh.memory.MemItem> allItems = new java.util.LinkedHashMap<>();
        for (com.dsh.memory.MemItem m : source.candidates(
                new com.dsh.memory.RecallQuery().limit(10000))) allItems.put(m.id, m);
        List<Long> fastMs = new ArrayList<>(), slowMs = new ArrayList<>();
        long c0 = System.nanoTime();
        source.idf();
        long coldMs = (System.nanoTime() - c0) / 1_000_000;
        long w0 = System.nanoTime();
        source.idf();
        long warmMs = (System.nanoTime() - w0) / 1_000_000;
        for (Object f : families) {
            Map<String, Object> fm = Json.asObject(f);
            if (fm == null) continue;
            String name = Json.string(fm.get("name"));
            List<Object> probes = Json.asArray(fm.get("probes"));
            int n = 0, hit1 = 0, hit3 = 0, hit5 = 0;
            if (probes != null) for (Object p : probes) {
                Map<String, Object> pm = Json.asObject(p);
                if (pm == null) continue;
                n++;
                long probeStart = System.nanoTime();
                String rawQuery = Json.string(pm.get("query"));
                String from = Json.string(pm.get("from"));
                String to = Json.string(pm.get("to"));
                com.dsh.memory.RecallQuery rawQ = new com.dsh.memory.RecallQuery()
                        .query(rawQuery).from(from).to(to)
                        .pkg(Json.string(pm.get("pkg")))
                        .level(Json.string(pm.get("level"))).limit(8);
                List<com.dsh.memory.RecallHit> rawHits =
                        com.dsh.memory.RecallCore.recall(source, rawQ);
                String anchor = anchorTerm(source, rawQuery);
                boolean anchorHit = false;
                for (com.dsh.memory.RecallHit h : rawHits) {
                    if (anchor != null && h.text != null && h.text.contains(anchor)) {
                        anchorHit = true;
                        break;
                    }
                }
                boolean forceStage2 = "E".equals(name)
                        || (from != null && !from.isEmpty() && rawHits.isEmpty());
                QueryUnderstand.Result u;
                List<com.dsh.memory.RecallHit> hits;
                String judgeVerdict = "未调用";
                if (anchorHit && !forceStage2) {
                    u = QueryUnderstand.understand(null, rawQuery, LocalDate.now());
                    hits = rawHits;
                    judgeVerdict = "stage1-anchor:" + anchor;
                } else {
                    CombinedQuery.Result cr = CombinedQuery.run(provider, rawQuery, rawHits);
                    u = new QueryUnderstand.Result(cr.keywords, null, null);
                    hits = rawHits;
                    judgeVerdict = cr.error ? "error" : (cr.none ? "none" : "match")
                            + (cr.reason == null || cr.reason.isEmpty() ? "" : " / " + cr.reason);
                    if (cr.none && !cr.error) hits = new ArrayList<>();
                    else if (hits.isEmpty() && !cr.keywords.isEmpty()) {
                        String eff = String.join(" ", cr.keywords);
                        hits = com.dsh.memory.RecallCore.recall(source,
                                new com.dsh.memory.RecallQuery().query(eff)
                                        .from(from).to(to).limit(8));
                    }
                }
                List<com.dsh.memory.RecallHit> before = new ArrayList<>(hits);
                long probeMs = (System.nanoTime() - probeStart) / 1_000_000;
                if (judgeVerdict.startsWith("stage1")) fastMs.add(probeMs);
                else slowMs.add(probeMs);
                List<Long> expected = longs(pm.get("expected_ids"));
                List<String> core = strings(pm.get("core_terms"));
                List<String> eterms = strings(pm.get("expected_terms"));
                String eday = Json.string(pm.get("expected_day"));
                boolean none = Boolean.TRUE.equals(pm.get("expect_none"));
                boolean missDiag = false;
                // 有效性检查（a–d）：可判定性，不是穷举完备性
                if (!none) {
                    List<String> bad = new ArrayList<>();
                    for (Long id : expected) if (!allItems.containsKey(id)) bad.add("a:id " + id);
                    int minDf = Integer.MAX_VALUE;
                    boolean hasTerm = false;
                    for (String t : eterms) {
                        if (t == null || t.isEmpty()) continue;
                        int df = 0;
                        for (com.dsh.memory.MemItem m : allItems.values()) {
                            if (m.text != null && m.text.contains(t)) df++;
                        }
                        minDf = Math.min(minDf, df);
                        for (Long id : expected) {
                            com.dsh.memory.MemItem m = allItems.get(id);
                            if (m != null && m.text != null && m.text.contains(t)) hasTerm = true;
                        }
                    }
                    if (!hasTerm) bad.add("b:expected 不含 expected_terms");
                    if (minDf > 20) bad.add("c:minDf=" + minDf);
                    if (expected.size() > 8) bad.add("d:expected=" + expected.size());
                    System.out.println("SELFCHECK2 " + (bad.isEmpty() ? "OK" : "INVALID")
                            + " [" + rawQuery + "] minDf=" + minDf + " " + bad);
                } else {
                    StringBuilder dfs = new StringBuilder();
                    for (String t : eterms) {
                        int df = 0;
                        for (com.dsh.memory.MemItem m : allItems.values()) {
                            if (m.text != null && m.text.contains(t)) df++;
                        }
                        dfs.append(t).append(" df=").append(df).append("; ");
                    }
                    System.out.println("SELFCHECK2 E [" + rawQuery + "] " + dfs);
                }
                if (none) {
                    if (!hits.isEmpty()) {
                        negFp++;
                        System.out.println("E_FP " + rawQuery + " top="
                                + top3(hits) + " judge=" + judgeVerdict);
                    }
                } else {
                    boolean found = false;
                    for (int i = 0; i < Math.min(5, hits.size()); i++) {
                        com.dsh.memory.RecallHit h = hits.get(i);
                        if (!core.isEmpty()) {
                            for (String term : core) {
                                if (h.text != null && h.text.contains(term)) {
                                    found = true;
                                    break;
                                }
                            }
                        } else if (expected.contains(h.itemId)) {
                            found = true;
                        }
                        if (found) {
                            if (i < 1) hit1++;
                            if (i < 3) hit3++;
                            hit5++;
                            break;
                        }
                    }
                    if (!found) {
                        missDiag = true;
                        System.out.println("MISS " + rawQuery);
                    }
                }
                if ("E".equals(name) || missDiag) {
                    System.out.println("[probe] " + rawQuery);
                    System.out.println("  QU.keywords = " + u.keywords);
                    System.out.println("  QU.from/to = " + from + " / " + to);
                    System.out.println("  候选数(judge 前) = " + before.size()
                            + "  候选数(judge 后) = " + hits.size());
                    System.out.println("  judge.verdict = " + judgeVerdict);
                    System.out.println("  top3(judge 前) = " + top3(before));
                    System.out.println("  top3(judge 后) = " + top3(hits));
                }
                if ("D".equals(name)) {
                    totalD += hits.size();
                    for (com.dsh.memory.RecallHit h : hits) {
                        if (h.hasPointer()) pointerOk++;
                    }
                }
                for (com.dsh.memory.RecallHit h : hits) {
                    if (h.detailLevel == com.dsh.memory.DetailLevel.APP_ONLY) appOnlyLeak++;
                }
            }
            double r1 = n == 0 ? 0 : (double) hit1 / n;
            double r3 = n == 0 ? 0 : (double) hit3 / n;
            double r5 = n == 0 ? 0 : (double) hit5 / n;
            System.out.printf("%s %d %.3f %.3f %.3f %s%n", name, n, r1, r3, r5,
                    "D".equals(name) ? (totalD == 0 ? "n/a" : String.format("%.3f",
                            (double) pointerOk / totalD)) : "-");
        }
        System.out.println("false_positive_on_negative=" + negFp);
        System.out.println("app_only_leak=" + appOnlyLeak);
        System.out.println("idf_cold_ms=" + coldMs + " idf_warm_ms=" + warmMs);
        System.out.println("fast_path_n=" + fastMs.size() + " fast_p50_ms=" + p50(fastMs)
                + " slow_path_n=" + slowMs.size() + " slow_p50_ms=" + p50(slowMs)
                + " all_p50_ms=" + p50(all(fastMs, slowMs))
                + " all_p95_ms=" + p95(all(fastMs, slowMs))
                + " fast_ratio=" + String.format("%.2f", fastMs.size() / 25.0));
        System.out.println("model_calls=" + ModelMeter.calls
                + " avg_calls_per_probe=" + String.format("%.2f",
                        ModelMeter.calls / 20.0)
                + " avg_latency_ms=" + String.format("%.1f",
                        ModelMeter.calls == 0 ? 0.0 : (double) ModelMeter.latencyMs / ModelMeter.calls)
                + " prompt_tokens=" + ModelMeter.promptTokens
                + " completion_tokens=" + ModelMeter.completionTokens
                + " p50_ms=" + ModelMeter.percentile(50)
                + " p95_ms=" + ModelMeter.percentile(95)
                + " est_cost_usd=" + String.format("%.4f", ModelMeter.costUsd()));
    }

    private static List<Long> all(List<Long> a, List<Long> b) {
        List<Long> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static long p50(List<Long> xs) {
        if (xs.isEmpty()) return 0;
        List<Long> c = new ArrayList<>(xs);
        java.util.Collections.sort(c);
        return c.get(c.size() / 2);
    }

    private static long p95(List<Long> xs) {
        if (xs.isEmpty()) return 0;
        List<Long> c = new ArrayList<>(xs);
        java.util.Collections.sort(c);
        return c.get(Math.min(c.size() - 1, (int) Math.ceil(c.size() * 0.95) - 1));
    }

    private static String anchorTerm(JdbcMemorySource source, String query) {
        com.dsh.memory.IdfModel idf = source.idf();
        if (idf == null || query == null) return null;
        String best = null;
        double bestIdf = -1;
        for (String g : com.dsh.memory.RecallRanker.queryGrams(query)) {
            if (idf.df.getOrDefault(g, 0) <= 0) continue;
            double v = idf.bm25Idf(g);
            if (v > bestIdf) { bestIdf = v; best = g; }
        }
        return best;
    }

    private static String top3(List<com.dsh.memory.RecallHit> hits) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, hits.size()); i++) {
            b.append(hits.get(i).itemId).append("(")
                    .append(String.format("%.2f", hits.get(i).coverage)).append(") ");
        }
        return b.toString();
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        List<Object> a = Json.asArray(o);
        if (a == null) return out;
        for (Object x : a) {
            String s = Json.string(x);
            if (s != null) out.add(s);
        }
        return out;
    }

    private static List<Long> longs(Object o) {
        List<Long> out = new ArrayList<>();
        List<Object> a = Json.asArray(o);
        if (a == null) return out;
        for (Object x : a) {
            if (x instanceof Number) out.add(((Number) x).longValue());
        }
        return out;
    }

    private static void migrateMemory(String[] args) throws Exception {
        String trace = ensureEvidence(workspacePath(arg(args, "--trace", "raw/v2/evidence.db")));
        String out = workspacePath(arg(args, "--out", "raw/v2/memory.db"));
        Class.forName("org.sqlite.JDBC");
        JdbcMemoryStore store = new JdbcMemoryStore(out);
        store.registerNamespace("trace", true);
        long n = 0, m = 0, full = 0, imageOnlyN = 0, appOnlyN = 0;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + trace);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,t0,t1,dwell_ms,pkg,activity,page_id,page_title,text,shot_ref,source,"
                             + "masked_text FROM traces ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                n++;
                long id = rs.getLong("id");
                String text = rs.getString("text");
                if (text == null || text.trim().isEmpty()) text = rs.getString("page_title");
                String shotRef = rs.getString("shot_ref");
                boolean hasText = text != null && !text.trim().isEmpty();
                boolean imageOnly = false;
                boolean appOnly = false;
                if (!hasText) {
                    text = "";
                    if (shotRef != null && !shotRef.trim().isEmpty()) imageOnly = true;
                    else appOnly = true;
                }
                long t0 = rs.getLong("t0"), t1 = rs.getLong("t1");
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("pkg", rs.getString("pkg"));
                d.put("activity", rs.getString("activity"));
                d.put("page_id", rs.getString("page_id"));
                d.put("page_title", rs.getString("page_title"));
                d.put("shot_ref", shotRef);
                d.put("dwell_ms", rs.getLong("dwell_ms"));
                d.put("source", rs.getString("source"));
                d.put("masked_text", rs.getString("masked_text"));
                if (imageOnly) d.put("image_only", true);
                if (appOnly) d.put("app_only", true);
                MemItem item = new MemItem("trace", Kind.BLOCK, "trace:" + id, text,
                        MemoryCodec.toJson(d));
                item.t0 = t0;
                item.t1 = t1;
                item.detailLevel = imageOnly ? com.dsh.memory.DetailLevel.IMAGE_ONLY
                        : appOnly ? com.dsh.memory.DetailLevel.APP_ONLY
                        : com.dsh.memory.DetailLevel.FULL;
                item.evidence.add(new Evidence("trace", String.valueOf(id), t0));
                store.put(item);
                m++;
                if (item.detailLevel == com.dsh.memory.DetailLevel.FULL) full++;
                else if (item.detailLevel == com.dsh.memory.DetailLevel.IMAGE_ONLY) imageOnlyN++;
                else appOnlyN++;
            }
        }
        System.out.println("migrate-memory: read N=" + n + " written M=" + m
                + " FULL=" + full + " IMAGE_ONLY=" + imageOnlyN
                + " APP_ONLY=" + appOnlyN);
        List<MemItem> sample = store.query(new Query().namespace("trace").limit(5));
        System.out.println("sample present=false check:");
        for (MemItem x : sample) {
            System.out.println("  #" + x.id + " present=" + MemoryPolicy.present(x)
                    + " text=" + x.text + " data=" + x.data);
        }
        store.close();
        System.out.println("memory db=" + out);
    }

    private static void day(String[] args) throws Exception {
        String date = arg(args, "--date", "2026-11-17");
        String db = workspacePath(arg(args, "--db", "raw/v2/memory.db"));
        String traceDb = ensureEvidence(workspacePath(arg(args, "--trace", "raw/v2/evidence.db")));
        String out = workspacePath(arg(args, "--out", "raw/v2/day_narratives.db"));
        String memDb = workspacePath(arg(args, "--mem", "raw/v2/memory.db"));
        String base = arg(args, "--base", "https://api.deepseek.com");
        String model = arg(args, "--model", "deepseek-chat");
        String key = readApiKey();
        if (key.isEmpty()) {
            System.err.println("API key 未配置（DSH_API_KEY 或 .secrets/deepseek.key）");
            System.exit(2);
        }
        Class.forName("org.sqlite.JDBC");
        LocalDate d = LocalDate.parse(date);
        ZoneId zone = ZoneId.systemDefault();
        long from = d.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        List<DayNarrativeBuilder.Event> events = new ArrayList<>();
        Map<Long, String> titles = new LinkedHashMap<>();
        StringBuilder ids = new StringBuilder();
        StringBuilder hashInput = new StringBuilder();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,title,body,t0,t1,n_blocks,outcome FROM chains "
                             + "WHERE level=0 AND status='done' AND t0 BETWEEN ? AND ? "
                             + "ORDER BY t0,id")) {
            ps.setLong(1, from);
            ps.setLong(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String title = rs.getString(2);
                    String body = rs.getString(3);
                    long t0 = rs.getLong(4), t1 = rs.getLong(5);
                    int nb = rs.getInt(6);
                    String oc = rs.getString(7);
                    if (oc == null || oc.isEmpty()) oc = "unknown";
                    events.add(new DayNarrativeBuilder.Event(id, title, body, t0, t1, nb, oc));
                    titles.put(id, title);
                    if (ids.length() > 0) ids.append(',');
                    ids.append(id);
                    hashInput.append(id).append('|').append(title).append('|')
                            .append(Sha1.of(body)).append('|').append(oc).append('\n');
                }
            }
        }
        System.out.println("day=" + date + " events=" + events.size());
        if (events.size() < 2) {
            System.out.println("skip: done events < 2");
            return;
        }
        List<com.dsh.trace.GapFinder.Gap> programGaps = dayGaps(traceDb, from, to);
        for (com.dsh.trace.GapFinder.Gap g : programGaps) {
            hashInput.append("gap|").append(g.text).append('\n');
        }
        List<DayNarrativeBuilder.Memory> memories = new ArrayList<>();
        StringBuilder memHash = new StringBuilder();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + memDb);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,text,evidence,t0 FROM mem_items WHERE superseded=0 "
                             + "AND namespace IN ('chat','manual') AND t0 BETWEEN ? AND ? "
                             + "ORDER BY t0,id")) {
            ps.setLong(1, from);
            ps.setLong(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ev = "chat";
                    try {
                        List<Evidence> es = MemoryCodec.parseEvidence(rs.getString(3));
                        if (!es.isEmpty() && es.get(0).type != null) ev = es.get(0).type;
                    } catch (Throwable ignored) { }
                    memories.add(new DayNarrativeBuilder.Memory(rs.getLong(1),
                            rs.getString(2), ev, rs.getLong(4)));
                    memHash.append(rs.getLong(1)).append('|')
                            .append(Sha1.of(rs.getString(2))).append('\n');
                }
            }
        }
        String inputSha = Sha1.of(hashInput.toString() + memHash);
        ensureOutTable(out);
        String oldSha = queryOldSha(out, date);
        if (inputSha.equals(oldSha)) {
            System.out.println("day narrative: reuse/skip (input unchanged)");
            return;
        }

        List<String> gaps = new ArrayList<>();
        for (com.dsh.trace.GapFinder.Gap g : programGaps) gaps.add(g.text);
        List<String> sources = daySources(traceDb, from, to);
        List<DayNarrativeBuilder.Parsed> holder = new ArrayList<>();
        List<String> lastErrors = new ArrayList<>();
        String firstError = "";
        String prompt = DayNarrativeBuilder.userPrompt(date, events, gaps, memories,
                "材料 " + events.size() + " 事件/" + memories.size() + " 条在场记忆");
        java.io.File outFile = new java.io.File(out);
        String auditPath = new java.io.File(outFile.getParentFile() == null
                ? "." : outFile.getParentFile().getPath(), "egress.log").getPath();
        EgressGate gate = new EgressGate(new Guard(), new FileAuditSink(auditPath));
        OpenAiCompatProvider provider = new OpenAiCompatProvider(
                "deepseek", base, key, model, 180_000,
                Collections.singleton(Capability.JSON), 128_000, CostClass.CHEAP,
                new UrlConnectionTransport(), gate, null);
        DayNarrativeBuilder.Parsed parsed = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String user = prompt + (lastErrors.isEmpty() ? ""
                    : "\n\n上一次输出未通过校验：" + String.join("；", lastErrors)
                    + "\n请修正后重新输出完整 JSON。");
            EgressResult gr = gate.checkProvenance("day.narrative",
                    DayNarrativeBuilder.SYSTEM, user, EgressTier.WHITE,
                    "deepseek", true, sources, EgressStage.DERIVED,
                    0, "FULL_CLOUD", 0, "");
            if (!gr.allowed) {
                System.err.println("egress rejected: " + gr.reason);
                System.exit(1);
            }
            ModelResponse r = provider.complete(ModelRequest.text(
                    gr.sanitizedSystem, gr.sanitizedUser)
                    .withThinking("enabled")
                    .withProvenance(sources, EgressStage.DERIVED));
            if (!r.isOk()) {
                firstError = "model: " + r.error;
                System.out.println("attempt " + attempt + " failed: " + firstError);
                continue;
            }
            try {
                parsed = DayNarrativeBuilder.parse(r.text);
                parsed = DayNarrativeBuilder.repair(parsed, events);
                lastErrors = DayNarrativeBuilder.validate(parsed, events, gaps,
                        !memories.isEmpty());
                if (lastErrors.isEmpty()) break;
                if (firstError.isEmpty()) firstError = String.join("；", lastErrors);
                System.out.println("attempt " + attempt + " validation: " + lastErrors);
            } catch (Throwable t) {
                firstError = "parse: " + t.getMessage();
                lastErrors = new ArrayList<>();
                lastErrors.add("JSON 解析失败");
                System.out.println("attempt " + attempt + " parse failed: " + t.getMessage());
            }
        }
        if (parsed == null) {
            System.err.println("FAILED: " + firstError);
            System.exit(1);
        }
        int partial = lastErrors.isEmpty() ? 0 : 1;
        if (partial == 0) firstError = "";
        System.out.println("C1-C4: " + (partial == 0 ? "PASS" : "PARTIAL " + firstError));
        String warn = DayNarrativeBuilder.narrativeWarning(parsed.narrative);
        if (!warn.isEmpty()) System.out.println("length warning: " + warn);
        System.out.println("narrative_sha1=" + Sha1.of(parsed.narrative)
                + " len=" + parsed.narrative.length());
        System.out.println("mainlines=" + parsed.mainlines.size()
                + " threads=" + parsed.threads.size() + " gaps=" + parsed.gaps.size());

        Set<Long> mentioned = new HashSet<>();
        for (DayNarrative.Mainline m : parsed.mainlines) {
            mentioned.addAll(m.eventIds);
            StringBuilder e = new StringBuilder();
            for (Long id : m.eventIds) {
                if (e.length() > 0) e.append(',');
                e.append(id).append(':').append(titles.getOrDefault(id, "?"));
            }
            System.out.println("  [" + m.title + " | " + m.timeHint + "] " + m.text
                    + "  -> " + e);
        }
        Set<Long> all = new HashSet<>(titles.keySet());
        Set<Long> covered = new HashSet<>(mentioned);
        covered.retainAll(all);
        int un = all.size() - covered.size();
        System.out.printf("coverage warning: mentioned=%d/%d unmentioned=%d (%.1f%%)%n",
                covered.size(), all.size(), un, 100.0 * un / Math.max(1, all.size()));
        for (DayNarrative.Thread t : parsed.threads) {
            System.out.println("  thread: " + t.text + " -> " + t.eventIds);
        }
        int largeTotal = 0, largeCovered = 0;
        java.util.Set<Long> cited = new java.util.HashSet<>();
        for (DayNarrative.Mainline m : parsed.mainlines) cited.addAll(m.eventIds);
        for (DayNarrativeBuilder.Event e : events) {
            if (e.blocks >= 8) {
                largeTotal++;
                if (cited.contains(e.id)) largeCovered++;
            }
        }
        int rawCovered = Math.max(0, largeCovered - parsed.repairedAdded);
        System.out.println("large_coverage model_raw=" + rawCovered + "/" + largeTotal
                + " after_repair=" + largeCovered + "/" + largeTotal
                + " repaired=" + parsed.repaired + " repaired_added=" + parsed.repairedAdded);
        store(out, date, parsed, ids.toString(), inputSha, partial, firstError, gaps);
        try {
            JdbcMemoryStore ms = new JdbcMemoryStore(memDb);
            ms.registerNamespace("day", true);
            StringBuilder summary = new StringBuilder();
            for (DayNarrative.Mainline m : parsed.mainlines) {
                if (summary.length() > 0) summary.append("；");
                summary.append(m.title.isEmpty() ? m.text : m.title);
            }
            MemItem dm = new MemItem("day", com.dsh.memory.Kind.DAY, "day:" + date,
                    summary.length() > 800 ? summary.substring(0, 800) : summary.toString(),
                    dayData(parsed, gaps));
            dm.evidence = java.util.Collections.singletonList(
                    new Evidence("trace", "day:" + date, from));
            dm.t0 = from;
            dm.t1 = to - 1;
            dm.detailLevel = com.dsh.memory.DetailLevel.FULL;
            ms.put(dm);
            ms.close();
            System.out.println("stored day memory key=day:" + date + " id=" + dm.id);
        } catch (Throwable t) {
            System.out.println("day memory write failed: " + t.getMessage());
        }
        System.out.println("stored day_narratives day=" + date + " partial=" + partial);
    }

    private static boolean onlyNarrativeLength(List<String> errs) {
        for (String e : errs) {
            if (e.startsWith("C4: narrative ") && e.contains("300–800")) continue;
            return false;
        }
        return !errs.isEmpty();
    }

    private static void ensureOutTable(String out) throws Exception {
        Path p = Paths.get(out);
        if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + out);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS day_narratives(" +
                    "day TEXT PRIMARY KEY,narrative TEXT,segments TEXT,mainlines TEXT," +
                    "open_threads TEXT,gaps TEXT,unmentioned_event_ids TEXT," +
                    "event_ids TEXT,input_sha1 TEXT," +
                    "index_partial INTEGER DEFAULT 0,validator_error TEXT,model TEXT," +
                    "prompt_tokens INTEGER,completion_tokens INTEGER," +
                    "created_at INTEGER,updated_at INTEGER)");
            try { s.execute("ALTER TABLE day_narratives ADD COLUMN mainlines TEXT"); }
            catch (Throwable ignored) { }
            try { s.execute("ALTER TABLE day_narratives ADD COLUMN repaired INTEGER DEFAULT 0"); }
            catch (Throwable ignored) { }
        }
    }

    private static String queryOldSha(String out, String day) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + out);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT input_sha1 FROM day_narratives WHERE day=?")) {
            ps.setString(1, day);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void store(String out, String day, DayNarrativeBuilder.Parsed p,
                              String ids, String sha, int partial, String err,
                              List<String> programGaps) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + out);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO day_narratives(day,narrative,segments,mainlines,"
                             + "open_threads,gaps,unmentioned_event_ids,event_ids,input_sha1,"
                             + "index_partial,validator_error,model,prompt_tokens,"
                             + "completion_tokens,created_at,updated_at,repaired) "
                             + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, day);
            ps.setString(2, p.narrative);
            ps.setString(3, segJson(p));
            ps.setString(4, mainJson(p));
            ps.setString(5, threadJson(p));
            ps.setString(6, stringsJson(programGaps));
            ps.setString(7, longsJson(p.unmentioned));
            ps.setString(8, ids);
            ps.setString(9, sha);
            ps.setInt(10, partial);
            ps.setString(11, err);
            ps.setString(12, "deepseek");
            ps.setInt(13, 0);
            ps.setInt(14, 0);
            long now = System.currentTimeMillis();
            ps.setLong(15, now);
            ps.setLong(16, now);
            ps.setInt(17, p.repaired ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private static String dayData(DayNarrativeBuilder.Parsed p, List<String> gaps) {
        StringBuilder b = new StringBuilder("{\"narrative\":").append(Json.quote(p.narrative))
                .append(",\"mainlines\":").append(mainJson(p))
                .append(",\"open_threads\":").append(threadJson(p))
                .append(",\"repaired\":").append(p.repaired)
                .append(",\"presence_notes\":[");
        for (int i = 0; i < p.presenceNotes.size(); i++) {
            if (i > 0) b.append(',');
            DayNarrativeBuilder.Presence x = p.presenceNotes.get(i);
            b.append("{\"text\":").append(Json.quote(x.text))
                    .append(",\"memory_ids\":").append(longsJson(x.memoryIds)).append('}');
        }
        return b.append("],\"gaps\":").append(stringsJson(gaps)).append('}').toString();
    }

    private static String mainJson(DayNarrativeBuilder.Parsed p) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < p.mainlines.size(); i++) {
            if (i > 0) b.append(',');
            DayNarrative.Mainline m = p.mainlines.get(i);
            b.append("{\"title\":").append(Json.quote(m.title))
                    .append(",\"text\":").append(Json.quote(m.text))
                    .append(",\"event_ids\":").append(longsJson(m.eventIds))
                    .append(",\"memory_ids\":").append(longsJson(m.memoryIds))
                    .append(",\"time_hint\":").append(Json.quote(m.timeHint)).append('}');
        }
        return b.append(']').toString();
    }

    private static String segJson(DayNarrativeBuilder.Parsed p) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < p.segments.size(); i++) {
            if (i > 0) b.append(',');
            DayNarrative.Segment s = p.segments.get(i);
            b.append("{\"text\":").append(Json.quote(s.text))
                    .append(",\"event_ids\":").append(longsJson(s.eventIds))
                    .append(",\"time_hint\":").append(Json.quote(s.timeHint)).append('}');
        }
        return b.append(']').toString();
    }

    private static String threadJson(DayNarrativeBuilder.Parsed p) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < p.threads.size(); i++) {
            if (i > 0) b.append(',');
            DayNarrative.Thread t = p.threads.get(i);
            b.append("{\"text\":").append(Json.quote(t.text))
                    .append(",\"event_ids\":").append(longsJson(t.eventIds))
                    .append(",\"memory_ids\":").append(longsJson(t.memoryIds)).append('}');
        }
        return b.append(']').toString();
    }

    private static String stringsJson(List<String> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(Json.quote(xs.get(i)));
        }
        return b.append(']').toString();
    }

    private static String longsJson(List<Long> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(xs.get(i));
        }
        return b.append(']').toString();
    }
}
