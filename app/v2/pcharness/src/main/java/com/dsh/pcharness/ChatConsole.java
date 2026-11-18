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
        ToolRegistry tools = new ToolRegistry();
        com.dsh.memory.MaskProvider masker = null;
        try { masker = new JdbcMaskProvider(evidence); } catch (Throwable ignored) { }
        tools.add(new RecallTool(source, prov, masker));
        tools.add(new RememberTool(source, "chat", ""));
        tools.add(new MemoryStatusTool(source::status));
        com.dsh.models.ModelRouter router = new com.dsh.models.ModelRouter(
                java.util.Collections.singletonList(provider), false, gate, null);
        LlmCaller caller = req -> {
            try {
                return router.execute(new SimpleTask("chat",
                        Collections.singleton(Capability.JSON), Quality.GOOD,
                        com.dsh.egress.EgressTier.WHITE), req);
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
            ChatLoop.run(host, ask, (ok, msg) -> {
                System.out.println(msg);
                if (!quiet) System.out.println("[turn] ok=" + ok);
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
            long t0 = System.currentTimeMillis();
            ChatLoop.run(host, line, (ok, msg) -> {
                System.out.println(msg);
                if (!quiet) {
                    System.out.println("[turn] ok=" + ok + " ms="
                            + (System.currentTimeMillis() - t0)
                            + " stage=" + prov.stage()
                            + " sources=" + prov.sources());
                }
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
