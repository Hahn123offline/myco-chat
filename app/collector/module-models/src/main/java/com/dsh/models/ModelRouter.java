package com.dsh.models;

import com.dsh.host.api.Config;
import com.dsh.host.api.Health;
import com.dsh.host.api.Log;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 模型路由器：全 app 模型调用的唯一实现。
 *
 * 职责（都在这一处，provider 里不许有）：
 *  1. 路由：taskId → provider + model（每次调用重新读配置 = 热加载）
 *  2. 并发：本地 provider 车道信号量 = 1，超出排队（手机上同时跑两个推理会 OOM）
 *  3. 超时：每个 provider 可配 timeoutMs；超时取消并返回 error 响应，不抛异常
 *  4. 降级：按配置的链依次尝试（chain 第二项就是"失败换 provider 重试"）
 *  5. health：本地 provider 靠真实探测（GET /v1/models）；云端靠真实调用结果；
 *     未知不等于 OK —— 没探测过就是 DEGRADED。
 *
 * 线程模型：complete() 只做提交，立刻返回；工作线程池是 daemon。
 * 每个 provider 调用再套一层 Future.get(timeout) 以便超时后能 cancel。
 * 本地车道由**内层任务**在 finally 里释放：超时只是不再等结果，
 * 真在跑的那次推理仍然占着车道（否则并发上限就是假的）。
 */
public final class ModelRouter implements LlmService, AutoCloseable {

    public static final String TAG = "models";
    private static final int MIN_QUEUE_WAIT_MS = 1;

    /** 生产代码只按 type 建两个 provider；测试注入假实现 */
    public interface ProviderFactory {
        LlmProvider create(ProviderConfig cfg);
    }

    /**
     * 每次取最新配置。**不能**只存一个 Config 快照：宿主每次 config() 给的是
     * ConfigStore 按 mtime 重新读出来的新对象，存快照 = 热更新失效。
     */
    private final Supplier<Config> configSource;
    private final ProviderFactory factory;
    private final ProviderProbe probe;
    private final Log log;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "models-io");
        t.setDaemon(true);
        return t;
    });

    /** 每个本地 provider 一条车道：并发上限 1（fair = 先到先得，避免插队） */
    private final Map<String, Semaphore> localLanes = new ConcurrentHashMap<>();
    /** 本地 provider 可达性（探测结果）；null = 尚未探测 */
    private final Map<String, Boolean> reachable = new ConcurrentHashMap<>();
    /** 本地 provider 最近一次探测详情（失败原因可诊断）；只在后台线程写入 */
    private final Map<String, ProbeResult> probeResults = new ConcurrentHashMap<>();
    /** 每个 provider 最近一次真实调用的错误；成功即清除 */
    private final Map<String, String> lastError = new ConcurrentHashMap<>();

    private volatile boolean closed = false;

    public ModelRouter(Supplier<Config> configSource, ProviderFactory factory,
                       ProviderProbe probe, Log log) {
        this.configSource = configSource;
        this.factory = factory;
        this.probe = probe;
        this.log = log;
    }

    /**
     * 静态配置便利构造（测试用）。
     * 生产路径必须传 Supplier（通常是 ctx::config），否则配置改了也读不到。
     */
    public ModelRouter(Config config, ProviderFactory factory, ProviderProbe probe, Log log) {
        this(() -> config, factory, probe, log);
    }

    private Config cfg() { return configSource.get(); }

    // ---------------- LlmService ----------------

    @Override
    public void complete(LlmRequest req, Callback cb) {
        if (cb == null) return;
        if (req == null) {
            deliver(cb, LlmResponse.error("request == null"));
            return;
        }
        if (closed) {
            deliver(cb, LlmResponse.error("model router 已关闭"));
            return;
        }
        final long t0 = System.nanoTime();
        try {
            pool.execute(() -> dispatch(req, t0, cb));
        } catch (RejectedExecutionException e) {
            deliver(cb, LlmResponse.error("model router 已关闭"));
        }
    }

    private void dispatch(LlmRequest req, long t0, Callback cb) {
        LlmResponse out;
        try {
            RouterConfig rc = RouterConfig.parse(cfg());
            List<RouteTarget> chain = RouterConfig.route(cfg(), req.taskId);
            if (chain.isEmpty()) {
                out = LlmResponse.error("没有为 task=" + req.taskId
                        + " 配置路由（task." + req.taskId + " / default 都是空）");
            } else if (rc.providers.isEmpty()) {
                out = LlmResponse.error("没有可用的 provider: " + String.join("；", rc.problems));
            } else {
                out = runChain(req, rc, chain);
            }
        } catch (Throwable t) {
            log.e(TAG, "dispatch threw for task=" + req.taskId, t);
            out = LlmResponse.error("路由内部错误: " + describe(t));
        }
        out.latencyMs = elapsedMs(t0);
        deliver(cb, out);
    }

    private LlmResponse runChain(LlmRequest req, RouterConfig rc, List<RouteTarget> chain) {
        StringBuilder errs = new StringBuilder();
        for (RouteTarget target : chain) {
            ProviderConfig base = rc.providers.get(target.provider);
            if (base == null) {
                appendErr(errs, target.provider + ": 未配置（检查 providers= 列表与 provider." + target.provider + ".*）");
                continue;
            }
            String model = target.model != null ? target.model : base.model;
            if (model == null || model.isEmpty()) {
                appendErr(errs, base.name + ": 未配置 model（provider 默认与路由都为空）");
                continue;
            }
            ProviderConfig eff = base.withModel(model);
            LlmResponse r;
            try {
                r = invoke(req, eff);
            } catch (Throwable t) {
                r = LlmResponse.error(eff.name + ": " + describe(t));
            }
            if (r.isOk()) {
                lastError.remove(base.name);
                return r;
            }
            lastError.put(base.name, r.error);
            appendErr(errs, eff.name + ":" + model + " -> " + r.error);
            log.w(TAG, "task=" + req.taskId + " 尝试 " + eff.name + ":" + model
                    + " 失败: " + r.error);
        }
        return LlmResponse.error(errs.length() == 0 ? "没有尝试任何 provider" : errs.toString());
    }

    /** 单次尝试：车道 → 提交 → 超时等结果；任何异常都变成 error 响应 */
    private LlmResponse invoke(final LlmRequest req, final ProviderConfig pc) {
        final LlmProvider provider;
        try {
            provider = factory.create(pc);
        } catch (Throwable t) {
            return LlmResponse.error(pc.name + ": 创建 provider 失败: " + describe(t));
        }

        final Semaphore lane = pc.local()
                ? localLanes.computeIfAbsent(pc.name, n -> new Semaphore(1, true))
                : null;
        long deadline = System.nanoTime() + pc.timeoutMs * 1_000_000L;

        if (lane != null) {
            long wait = remainingMs(deadline);
            if (wait < MIN_QUEUE_WAIT_MS) return LlmResponse.error(pc.name + ": 排队等待超时");
            boolean got;
            try {
                got = lane.tryAcquire(wait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LlmResponse.error(pc.name + ": 排队被中断");
            }
            if (!got) {
                return LlmResponse.error(pc.name + ": 本地车道忙，等待 " + pc.timeoutMs + "ms 仍未轮到");
            }
        }

        Future<LlmResponse> f;
        try {
            long remain = remainingMs(deadline);
            if (remain < MIN_QUEUE_WAIT_MS) {
                if (lane != null) lane.release();
                return LlmResponse.error(pc.name + ": 超时 " + pc.timeoutMs + "ms");
            }
            // 车道由内层任务释放：不因为"不再等结果"就假装推理没在跑
            f = pool.submit(() -> {
                try {
                    return provider.complete(req);
                } catch (Throwable t) {
                    return LlmResponse.error(pc.name + ": " + describe(t));
                } finally {
                    if (lane != null) lane.release();
                }
            });
        } catch (RejectedExecutionException e) {
            if (lane != null) lane.release();
            return LlmResponse.error(pc.name + ": router 已关闭");
        }

        try {
            return f.get(remainingMs(deadline), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            f.cancel(true);
            return LlmResponse.error(pc.name + ": 超时 " + pc.timeoutMs + "ms");
        } catch (InterruptedException ie) {
            f.cancel(true);
            Thread.currentThread().interrupt();
            return LlmResponse.error(pc.name + ": 被中断");
        } catch (ExecutionException ee) {
            return LlmResponse.error(pc.name + ": "
                    + describe(ee.getCause() == null ? ee : ee.getCause()));
        }
    }

    // ---------------- health ----------------

    /**
     * 如实上报，不假装 OK：
     *  - 没有任何可用 provider → FAILED（配置问题写明）
     *  - 有本地 provider 不可达 → 若还有云端可用则 DEGRADED，全不可用则 FAILED
     *  - 本地 provider 尚未探测 → DEGRADED（未知不是健康）
     *  - 某 provider 最近一次真实调用失败 → DEGRADED（成功即恢复）
     */
    @Override
    public Health health() {
        if (closed) return Health.stopped();
        try {
            RouterConfig rc = RouterConfig.parse(cfg());
            if (rc.providers.isEmpty()) {
                // "没配置" ≠ "坏了"：新装机器上没配任何 provider 是正常状态。
                // 用 DEGRADED + 明确 detail，避免前台通知把它当故障吓用户。
                StringBuilder why = new StringBuilder("no-providers-configured");
                if (!rc.problems.isEmpty()) why.append(": ").append(String.join("；", rc.problems));
                return Health.degraded(why.toString());
            }
            List<String> down = new ArrayList<>();
            boolean anyUsable = false;
            boolean unknownLocal = false;

            for (ProviderConfig pc : rc.providers.values()) {
                if (pc.local()) {
                    Boolean up = reachable.get(pc.name);
                    if (up == null) {
                        unknownLocal = true;
                    } else if (!up) {
                        ProbeResult pr = probeResults.get(pc.name);
                        down.add("本地 " + pc.name + "@" + pc.baseUrl + " 不可达"
                                + (pr == null ? "" : "(" + pr.forLog() + ")"));
                        continue;
                    } else {
                        anyUsable = true;
                    }
                } else {
                    // 云端不主动探测；可用性由真实调用结果更新
                    anyUsable = true;
                }
                String err = lastError.get(pc.name);
                if (err != null) down.add(pc.name + " 最近一次调用失败: " + err);
            }

            if (!down.isEmpty()) {
                String why = String.join("；", down);
                return anyUsable ? Health.degraded(why) : Health.failed(why);
            }
            if (unknownLocal) return Health.degraded("本地 provider 尚未探测");
            return Health.ok();
        } catch (Throwable t) {
            return Health.failed("health() threw: " + describe(t));
        }
    }

    /**
     * 同步探测所有本地 provider。
     *
     * ⚠️ **必须在后台线程调用**（定时任务请用 {@link #probeAsync()}）。
     * 在主线程里发 HTTP 会触发 NetworkOnMainThreadException，被吞成"服务 DOWN"——
     * 这是真机上真实踩过的 bug（adb reverse 通路明明是通的）。
     */
    public void probeNow() {
        if (closed) return;
        try {
            RouterConfig rc = RouterConfig.parse(cfg());
            Set<String> configuredLocal = new HashSet<>();
            for (ProviderConfig pc : rc.providers.values()) {
                if (!pc.local()) continue;
                configuredLocal.add(pc.name);
                ProbeResult r;
                try {
                    r = probe.probe(pc);
                } catch (Throwable t) {
                    r = ProbeResult.of(t);
                }
                if (r == null) r = ProbeResult.down("exception-null", "probe returned null");
                reachable.put(pc.name, r.up);
                probeResults.put(pc.name, r);
                if (r.up) {
                    log.i(TAG, "probe " + pc.name + " UP (" + pc.baseUrl + ")");
                } else {
                    // 失败日志必须带异常类名 + message（只有 baseUrl 时无法诊断）
                    log.w(TAG, "probe " + pc.name + " DOWN (" + pc.baseUrl + ") " + r.forLog());
                }
            }
            // 已经不在配置里的本地 provider 清掉，避免残留状态影响 health
            reachable.keySet().retainAll(configuredLocal);
            probeResults.keySet().retainAll(configuredLocal);
        } catch (Throwable t) {
            log.e(TAG, "probe threw", t);
        }
    }

    /**
     * 异步探测：install / 定时任务 / 状态刷新里都只调这个。
     * 实际 HTTP 在 "models-io" daemon 线程上跑，主线程不会触发 NetworkOnMainThreadException。
     */
    public void probeAsync() {
        if (closed) return;
        try {
            pool.execute(this::probeNow);
        } catch (RejectedExecutionException ignored) {
            // 已关闭
        }
    }

    /** 最近一次探测详情（测试/状态页用） */
    public ProbeResult probeResult(String providerName) {
        return probeResults.get(providerName);
    }

    /** 供状态页/测试看：当前配置解析出的 provider 列表 */
    public Map<String, ProviderConfig> providers() {
        return RouterConfig.parse(cfg()).providers;
    }

    @Override
    public void close() {
        closed = true;
        // 已提交的调用允许跑完（每个都有 timeout 上界，且会恰好回调一次）；
        // 卸载之后的新调用被 closed 挡住，立刻返回 error。线程是 daemon，不拖进程退出。
        pool.shutdown();
        localLanes.clear();
    }

    // ---------------- 工具 ----------------

    private void deliver(Callback cb, LlmResponse resp) {
        try {
            cb.onResult(resp);
        } catch (Throwable t) {
            log.e(TAG, "callback threw", t);
        }
    }

    private static long remainingMs(long deadlineNanos) {
        long ns = deadlineNanos - System.nanoTime();
        if (ns <= 0) return 0;
        return Math.max(1, ns / 1_000_000L);
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static void appendErr(StringBuilder sb, String msg) {
        if (sb.length() > 0) sb.append(" | ");
        sb.append(msg);
    }

    private static String describe(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
    }
}
