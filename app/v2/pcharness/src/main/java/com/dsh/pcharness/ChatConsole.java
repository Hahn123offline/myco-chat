package com.dsh.pcharness;

import com.dsh.agent.AgentEngine;
import com.dsh.agent.AgentStore;
import com.dsh.agent.ChatHost;
import com.dsh.agent.ChatLoop;
import com.dsh.agent.LlmCaller;
import com.dsh.agent.MemoryStatusTool;
import com.dsh.agent.Provenance;
import com.dsh.agent.PromptAssembler;
import com.dsh.agent.RecallTool;
import com.dsh.agent.RememberTool;
import com.dsh.agent.ToolRegistry;
import com.dsh.egress.EgressStage;
import com.dsh.memory.MemorySource;
import com.dsh.models.Capability;
import com.dsh.models.CostClass;
import com.dsh.models.OpenAiCompatProvider;
import com.dsh.models.Quality;
import com.dsh.models.SimpleTask;
import com.dsh.models.UrlConnectionTransport;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/** PC 终端持久会话：复用 ChatLoop/AgentEngine/tools/PromptAssembler。 */
public final class ChatConsole {

    private ChatConsole() { }

    public static void run(String[] args) throws Exception {
        String mem = Main.workspacePath(Main.arg(args, "--mem", "raw/v2/device/host_memory.db"));
        String evidence = Main.workspacePath(Main.arg(args, "--evidence",
                "raw/v2/device/evidence.db"));
        String ask = Main.arg(args, "--ask", null);
        boolean quiet = args.length > 0 && java.util.Arrays.asList(args).contains("--quiet");
        String key = Main.readApiKey();
        if (key.isEmpty()) {
            System.out.println("API key 未配置");
            return;
        }
        String audit = Main.workspacePath(Main.arg(args, "--audit",
                "app/v2/pcharness/out/chat-egress.log"));
        com.dsh.egress.EgressGate gate = new com.dsh.egress.EgressGate(
                new com.dsh.guard.Guard(), new FileAuditSink(audit));
        OpenAiCompatProvider provider = new OpenAiCompatProvider("deepseek",
                Main.arg(args, "--base", "https://api.deepseek.com"),
                key, Main.arg(args, "--model", "deepseek-chat"), 180_000,
                Collections.singleton(Capability.JSON), 128_000, CostClass.CHEAP,
                new UrlConnectionTransport(), gate, null);
        JdbcMemorySource source = new JdbcMemorySource(mem);
        JdbcAgentStore store = new JdbcAgentStore(mem);
        PcProvenance prov = new PcProvenance();
        final List<String> recalls = new ArrayList<>();
        final long[] usage = new long[3]; // prompt, completion, latency
        final String[] currentUser = {""};
        ToolRegistry tools = new ToolRegistry();
        com.dsh.memory.MaskProvider masker = null;
        try { masker = new JdbcMaskProvider(evidence); } catch (Throwable ignored) { }
        tools.add(new RecallTool(source, prov, masker, recalls::add));
        tools.add(new RememberTool(source, "chat", () -> currentUser[0]));
        tools.add(new MemoryStatusTool(source::status));
        tools.add(new com.dsh.agent.DayOverviewTool(source));
        tools.add(new com.dsh.agent.CorrectTool(source));
        tools.add(new com.dsh.agent.ForgetTool(source));
        com.dsh.models.ModelRouter router = new com.dsh.models.ModelRouter(
                java.util.Collections.singletonList(provider), false, gate, null);
        LlmCaller caller = req -> {
            try {
                com.dsh.models.ModelResponse r = router.execute(new SimpleTask("chat",
                        Collections.singleton(Capability.JSON), Quality.GOOD,
                        com.dsh.egress.EgressTier.WHITE), req);
                usage[0] += r.promptTokens;
                usage[1] += r.completionTokens;
                usage[2] += r.latencyMs;
                return r;
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        String sim = readSim();
        AgentEngine.Config cfg = new AgentEngine.Config();
        cfg.thinking = "enabled";
        cfg.sources = prov.sources;
        cfg.strippedSources = prov.stripped;
        cfg.stageSupplier = () -> prov.stage();
        cfg.appPolicy = "DERIVED";

        ChatHost host = new ChatHost() {
            @Override public String sessionId() { return "chat"; }
            @Override public String systemPrompt() {
                return PromptAssembler.assemble("", sim,
                        "当前时间：" + new java.util.Date() + "\n这是 PC 端离线会话。");
            }
            @Override public AgentStore store() { return store; }
            @Override public ToolRegistry tools() { return tools; }
            @Override public LlmCaller caller() { return caller; }
            @Override public AgentEngine.Config config() { return cfg; }
        };

        if (ask != null) {
            currentUser[0] = ask;
            ChatLoop.run(host, ask, (ok, msg) -> {
                System.out.println(msg);
                if (!quiet) trace(host, prov, recalls, usage, audit);
            });
            return;
        }
        System.out.println("chat 会话已启动（输入 exit 退出，历史持久在 " + mem + "）");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            System.out.flush();
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine();
            if (line == null || line.trim().isEmpty()) continue;
            if ("exit".equalsIgnoreCase(line.trim())) break;
            currentUser[0] = line;
            long t0 = System.currentTimeMillis();
            ChatLoop.run(host, line, (ok, msg) -> {
                System.out.println(msg);
                if (!quiet) trace(host, prov, recalls, usage, audit);
            });
        }
    }


    public static void dump(String[] args) throws Exception {
        String mem = Main.workspacePath(Main.arg(args, "--mem", "raw/v2/device/host_memory.db"));
        String evidence = Main.workspacePath(Main.arg(args, "--evidence",
                "raw/v2/device/evidence.db"));
        String date = Main.arg(args, "--date", java.time.LocalDate.now().toString());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + mem)) {
            System.out.println("date=" + date + " memory=" + mem);
            for (String t : new String[]{"mem_items", "chains", "day_narratives", "agent_events"}) {
                try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM " + t)) {
                    rs.next();
                    System.out.println("  " + t + "=" + rs.getInt(1));
                }
            }
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT namespace,COUNT(*) FROM mem_items GROUP BY namespace")) {
                while (rs.next()) System.out.println("  mem " + rs.getString(1) + "=" + rs.getInt(2));
            }
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT narrative FROM day_narratives WHERE day='"
                            + date.replace("'", "''") + "'")) {
                if (rs.next()) System.out.println("day_narrative=" + rs.getString(1));
            }
            System.out.println("最近 5 条 memory：");
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT namespace,text,t0,evidence FROM mem_items ORDER BY id DESC LIMIT 5")) {
                while (rs.next()) {
                    String ev = rs.getString(4) == null ? "" : rs.getString(4);
                    boolean present = ev.contains("\"chat\"") || ev.contains("\"manual\"");
                    System.out.println("  " + rs.getString(1) + " | present=" + present
                            + " | " + new java.util.Date(rs.getLong(3)) + " | "
                            + shorten(rs.getString(2)));
                }
            }
        }
        String audit = Main.workspacePath(Main.arg(args, "--audit",
                "app/v2/pcharness/out/chat-egress.log"));
        int derived = 0, masked = 0, blocked = 0, total = 0;
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(audit))) {
                if (line.contains("\"task\":\"provider\"")) {
                    total++;
                    if (line.contains("\"stage\":\"DERIVED\"")) derived++;
                    if (line.contains("\"stage\":\"MASKED\"")) masked++;
                    if (line.contains("\"allowed\":false")) blocked++;
                }
            }
            System.out.println("egress：provider 记录=" + total + " DERIVED=" + derived
                    + " MASKED=" + masked + " blocked=" + blocked);
        } catch (Throwable t) {
            System.out.println("egress 审计不可读: " + t.getMessage());
        }
        try (Connection e = DriverManager.getConnection("jdbc:sqlite:" + evidence)) {
            for (String t : new String[]{"traces", "page_elements"}) {
                try (ResultSet rs = e.createStatement().executeQuery("SELECT COUNT(*) FROM " + t)) {
                    rs.next();
                    System.out.println("  evidence " + t + "=" + rs.getInt(1));
                }
            }
        } catch (Throwable t) {
            System.out.println("  evidence 缺失/不可读: " + t.getMessage());
        }
    }

    private static void trace(ChatHost host, PcProvenance prov, List<String> recalls,
                              long[] usage, String audit) {
        for (String r : recalls) System.out.println("[recall] " + r);
        recalls.clear();
        System.out.println("[egress] stage=" + prov.stage() + " sources=" + prov.sources()
                + " stripped=" + prov.stripped);
        int msgs = host.store().events(host.sessionId()).size();
        int chars = 0;
        for (com.dsh.agent.AgentEvent e : host.store().events(host.sessionId())) {
            chars += e.payload == null ? 0 : e.payload.length();
        }
        System.out.println("[context] transcript=" + msgs + " msgs, est_chars=" + chars);
        double cny = usage[0] / 1_000_000.0 * 1.0 + usage[1] / 1_000_000.0 * 4.0;
        System.out.println("[cost] prompt_tokens=" + usage[0] + " completion_tokens="
                + usage[1] + " latency_ms=" + usage[2]
                + " est_cny=" + String.format("%.4f", cny));
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(audit),
                    ("{\"task\":\"chat.turn.summary\",\"stage\":\"" + prov.stage()
                            + "\",\"sourcesCount\":" + prov.sources.size()
                            + ",\"strippedCount\":" + prov.stripped.size()
                            + ",\"prompt_tokens\":" + usage[0]
                            + ",\"completion_tokens\":" + usage[1] + "}\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) { }
        usage[0] = usage[1] = usage[2] = 0;
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String x = s.replace('\n', ' ');
        return x.length() <= 40 ? x : x.substring(0, 40) + "…";
    }

    private static String readSim() {
        String[] paths = {"app/v2/app/src/main/assets/prompt/sim.md",
                "../app/src/main/assets/prompt/sim.md"};
        for (String p : paths) {
            try {
                if (Files.exists(Paths.get(p))) {
                    return new String(Files.readAllBytes(Paths.get(p)),
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                }
            } catch (Throwable ignored) { }
        }
        return "";
    }

    static final class PcProvenance implements Provenance {
        final List<String> sources = new ArrayList<>();
        final List<String> stripped = new ArrayList<>();
        boolean recalled;
        int missing;
        String status = "";

        @Override public List<String> sources() { return sources; }
        @Override public List<String> strippedSources() { return stripped; }
        @Override public boolean recalled() { return recalled; }
        @Override public void markRecalled() { recalled = true; }
        @Override public int maskedMissing() { return missing; }
        @Override public void addMaskedMissing(int n) { missing += n; }
        @Override public String maskStatus() { return status; }
        @Override public void setMaskStatus(String s) { status = s == null ? "" : s; }
        @Override public EgressStage stage() {
            return recalled ? EgressStage.MASKED : EgressStage.DERIVED;
        }
    }
}
