package com.dsh.usagetrace;

import com.dsh.host.api.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略表：默认策略 + 按包名覆盖。
 *
 * 配置格式（Config 键 "policy"），分号分隔：
 *   "com.tencent.mm=tick:10000:ocr:0.25;com.android.browser=event"
 * 字段：mode[:tickMs][:ocr][:titleRegion]
 */
public final class PolicyTable {

    private final CapturePolicy def;
    private final Map<String, CapturePolicy> byPkg = new HashMap<>();

    public PolicyTable(CapturePolicy def) { this.def = def; }

    public CapturePolicy forPkg(String pkg) {
        CapturePolicy p = byPkg.get(pkg);
        return p != null ? p : new CapturePolicy(pkg, def.mode, def.tickMs, def.ocr, def.titleRegion);
    }

    public void put(CapturePolicy p) { byPkg.put(p.pkg, p); }

    public int size() { return byPkg.size(); }

    /** 从配置加载；解析失败的行被忽略（不因为配置烂掉就停采集） */
    public static PolicyTable fromConfig(Config cfg, CapturePolicy def) {
        PolicyTable t = new PolicyTable(def);
        String raw = cfg.getString("policy", "");
        if (raw == null || raw.trim().isEmpty()) return t;
        for (String line : raw.split(";")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String pkg = line.substring(0, eq).trim();
            String spec = line.substring(eq + 1).trim();
            try {
                t.put(parse(pkg, spec));
            } catch (Exception ignored) { }
        }
        return t;
    }

    private static CapturePolicy parse(String pkg, String spec) {
        String[] parts = spec.split(":");
        CapturePolicy.Mode mode = CapturePolicy.Mode.valueOf(parts[0].trim().toUpperCase());
        long tick = 10000;
        boolean ocr = false;
        float region = 0.25f;
        for (int i = 1; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.isEmpty()) continue;
            if (s.equalsIgnoreCase("ocr")) ocr = true;
            else if (s.matches("\\d+")) tick = Long.parseLong(s);
            else if (s.matches("\\d*\\.\\d+")) region = Float.parseFloat(s);
        }
        return new CapturePolicy(pkg, mode, tick, ocr, region);
    }

    /** 内置默认：微信/QQ 节点树不可用 → TICK + OCR；浏览器 → EVENT */
    public static PolicyTable defaults() {
        PolicyTable t = new PolicyTable(CapturePolicy.def("*"));
        t.put(new CapturePolicy("com.tencent.mm", CapturePolicy.Mode.TICK, 10_000, true, 0.25f));
        t.put(new CapturePolicy("com.tencent.mobileqq", CapturePolicy.Mode.TICK, 10_000, true, 0.25f));
        t.put(new CapturePolicy("com.android.browser", CapturePolicy.Mode.EVENT, 0, false, 0.25f));
        t.put(new CapturePolicy("tv.danmaku.bili", CapturePolicy.Mode.EVENT, 0, true, 0.45f));
        t.put(new CapturePolicy("com.xingin.xhs", CapturePolicy.Mode.EVENT, 0, false, 0.25f));
        return t;
    }
}
