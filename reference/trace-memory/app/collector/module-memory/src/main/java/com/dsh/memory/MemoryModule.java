package com.dsh.memory;

import android.content.Context;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Config;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.Module;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.events.EpisodeClosed;
import com.dsh.host.api.events.EpisodeRedactionUpgraded;
import com.dsh.host.api.events.EpisodeRedacted;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 记忆模块：本期只做「存 + 查 + 就地升级脱敏等级 + 出网门控」。
 *
 * 门控（{@link RuleEgressPolicy}）纯规则、不引模型：本地只决定"这条能不能出设备"，
 * 内容理解交给云端。拿不准一律 GRAY，绝不放行。
 */
public final class MemoryModule implements Module {

    /** 高隐私来源不许进入主动提及；本期先只打标，不做提通道 */
    private static final Set<String> HIGH_TIER_PKGS = new java.util.HashSet<>(Arrays.asList(
            "com.tencent.mm", "com.tencent.mobileqq", "com.android.mms"));

    private final Context app;
    private volatile Health health = Health.stopped();
    private MemoryStore store;
    private RuleEgressPolicy policy;
    private HostContext ctx;
    private long saved = 0;
    private long upgraded = 0;
    private volatile String lastExportPath = "";

    public MemoryModule(Context app) { this.app = app; }

    @Override public String id() { return "memory"; }
    @Override public String version() { return "1.2.0-dev"; }

    @Override public List<Capability> requires() { return java.util.Collections.emptyList(); }

    @Override
    public void install(HostContext ctx) {
        this.ctx = ctx;
        store = new MemoryStore(app);
        policy = new RuleEgressPolicy(ctx::config);
        ctx.provide(EgressPolicy.class, policy);

        // 记住最近一次 episode 的时间窗与标题（脱敏事件里没带这些）
        final long[] lastWindow = new long[]{0, 0};
        final String[] lastTitle = new String[]{""};
        ctx.on(EpisodeClosed.class, ev -> {
            lastWindow[0] = ev.episode.tStart;
            lastWindow[1] = ev.episode.tEnd;
            lastTitle[0] = ev.episode.title == null ? "" : ev.episode.title;
        });

        ctx.on(EpisodeRedacted.class, ev -> {
            try {
                long t0 = lastWindow[0] > 0 ? lastWindow[0] : ev.ts;
                long t1 = lastWindow[1] > 0 ? lastWindow[1] : ev.ts;
                String masked = ev.maskedText == null ? "" : ev.maskedText;
                // 门控：纯规则，只看 pkg/标题/已脱敏正文；拿不准 → GRAY
                EgressTier tier = policy.tierFor(ev.pkg, "", lastTitle[0], masked);
                MemoryBlock b = MemoryMapper.from(ev, lastTitle[0], t0, t1, tier);
                store.insert(b);
                saved++;
                ctx.log().i(id(), "saved block " + b.id + " pkg=" + b.pkg
                        + " tier=" + b.privacyTier + " level=" + RedactionLevel.name(b.redactionLevel)
                        + " egress=" + b.egressTier + " total=" + store.count());
            } catch (Throwable t) {
                ctx.log().e(id(), "save failed", t);
            }
        });

        // 实时层模型补充成功 → 就地升级同一条块（找不到就等批处理兜）
        ctx.on(EpisodeRedactionUpgraded.class, ev -> {
            try {
                int rows = store.updateRedaction(ev.episodeId, ev.maskedText,
                        ev.redactionLevel, ev.ts);
                if (rows > 0) {
                    upgraded++;
                    ctx.log().i(id(), "upgraded episode=" + ev.episodeId
                            + " to " + RedactionLevel.name(ev.redactionLevel));
                } else {
                    ctx.log().w(id(), "upgrade missed episode=" + ev.episodeId
                            + "（块还没落库或已是同等级；批处理会再兜）");
                }
            } catch (Throwable t) {
                ctx.log().e(id(), "upgrade failed for " + ev.episodeId, t);
            }
        });

        // 对外提供查询服务
        ctx.provide(MemoryQuery.class, store);

        // LIKE 候选池上限（配置热加载；默认 800）
        store.setCandidateCap(ctx.config().getInt("memory.search.candidateCap",
                LikeRanker.DEFAULT_CANDIDATE_CAP));

        // 调试导出：周期把最近 N 条写成 JSON（adb 可拉）；顺带做检索自测（demoQuery 可配）
        long exportPeriod = Math.max(10_000L, ctx.config().getLong("memory.export.periodMs", 60_000L));
        ctx.schedule("memory-export", exportPeriod, this::exportNow);
        // install 立刻写一次：避免真机刚升级时看到的是旧 build 的 stale export_recent.json
        exportNow();

        ctx.effect(() -> { if (store != null) store.close(); });

        ctx.log().i(id(), "installed; blocks=" + store.count()
                + " searchEngine=" + store.searchEngine()
                + " reason=" + store.searchEngineReason());
        try {
            // 固定 tag 的直接日志：不依赖宿主 Log 封装，adb logcat -s DSHFts 可见
            android.util.Log.i(MemoryStore.TAG_FTS, "installed engine=" + store.searchEngine()
                    + " reason=" + store.searchEngineReason()
                    + " dbVer=" + MemorySchema.DB_VER
                    + " candidateCap=" + store.candidateCap());
        } catch (Throwable ignored) {
            // 某些 ROM 限制 logcat 时不能影响 install
        }
        if (!MemoryFtsInstaller.ENGINE_FTS5.equals(store.searchEngine())) {
            ctx.log().w(id(), MemoryFtsInstaller.degradedDetail(
                    store.searchEngine(), store.searchEngineReason()));
        }
        health = Health.ok();
    }

    /** 写一次调试导出（install 与周期任务共用；失败不影响主流程） */
    private void exportNow() {
        try {
            Config c = ctx.config();
            int n = clamp(c.getInt("memory.export.n", 200), 1, 2_000);
            store.setCandidateCap(c.getInt("memory.search.candidateCap",
                    LikeRanker.DEFAULT_CANDIDATE_CAP));
            String demo = c.getString("memory.export.demoQuery", "").trim();
            File f = store.writeExport(n, demo, episodeTotal());
            if (f != null) {
                lastExportPath = f.getAbsolutePath();
                ctx.log().i(id(), "exported " + n + " blocks"
                        + (demo.isEmpty() ? "" : " + demoQuery='" + demo + "'")
                        + " engine=" + store.searchEngine()
                        + "(" + store.searchEngineReason() + ")"
                        + " reconcile=" + reconcileLine()
                        + " -> " + lastExportPath);
            }
        } catch (Throwable t) {
            ctx.log().e(id(), "export failed", t);
        }
    }

    public long saved() { return saved; }
    public long upgraded() { return upgraded; }

    /** 状态页/verify_pipeline 用：块数 + 各出网档位数量 + 条数对账 + 检索引擎 + 导出路径 */
    public String debugSummary() {
        MemoryStore s = store;
        if (s == null) return "blocks=0";
        try {
            long episodes = episodeTotal();
            long blocks = s.count();
            long diff = episodes >= 0 ? episodes - blocks : -1;
            return "episodes=" + episodes + " blocks=" + blocks + " diff=" + diff
                    + " engine=" + s.searchEngine() + "(" + s.searchEngineReason() + ")"
                    + " labels ok=" + s.countByLabelStatus(com.dsh.host.api.data.LabelStatus.OK)
                    + " pending=" + s.countByLabelStatus(com.dsh.host.api.data.LabelStatus.PENDING)
                    + " white=" + s.countByEgressTier(EgressTier.WHITE)
                    + " gray=" + s.countByEgressTier(EgressTier.GRAY)
                    + " black=" + s.countByEgressTier(EgressTier.BLACK)
                    + " unknown=" + s.countByEgressTier(EgressTier.UNKNOWN)
                    + (lastExportPath.isEmpty() ? "" : " export=" + lastExportPath);
        } catch (Throwable t) {
            return "blocks=?";
        }
    }

    /** "episodes=N / blocks=M / 差=K" 一行（日志/状态页/导出共用） */
    public String reconcileLine() {
        long episodes = episodeTotal();
        long blocks;
        try { blocks = store == null ? -1 : store.count(); } catch (Throwable t) { blocks = -1; }
        long diff = (episodes >= 0 && blocks >= 0) ? episodes - blocks : -1;
        return "episodes=" + episodes + " / blocks=" + blocks + " / 差=" + diff;
    }

    /** usagetrace 提供的只读统计（拿不到返回 -1，不编造） */
    private long episodeTotal() {
        HostContext c = ctx;
        if (c == null) return -1;
        try {
            com.dsh.host.api.trace.EpisodeStats stats =
                    c.require(com.dsh.host.api.trace.EpisodeStats.class);
            return stats == null ? -1 : stats.countTotal();
        } catch (Throwable t) {
            return -1;
        }
    }

    public String lastExportPath() { return lastExportPath; }

    /**
     * 如实上报：FTS5 不可用（= 检索质量下降）时 DEGRADED，detail 里带 searchEngine。
     * 降级绝对不能静默：状态页/health/导出 JSON 三处都能看到。
     */
    @Override
    public Health health() {
        MemoryStore s = store;
        if (s == null) return health;
        String engine;
        String reason;
        try {
            engine = s.searchEngine();
            reason = s.searchEngineReason();
        } catch (Throwable t) {
            return Health.failed("searchEngine probe threw: " + t.getClass().getSimpleName());
        }
        if (MemoryFtsInstaller.ENGINE_FTS5.equals(engine)) {
            return Health.ok(MemoryFtsInstaller.degradedDetail(engine, reason));
        }
        return Health.degraded(MemoryFtsInstaller.degradedDetail(engine, reason));
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
