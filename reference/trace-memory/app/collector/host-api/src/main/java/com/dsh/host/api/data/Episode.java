package com.dsh.host.api.data;

/**
 * 一次「注意」的记录单元。这是模块之间的共享契约（usagetrace 生产，下游消费），
 * 所以放在 host-api，而不是 usagetrace 里。
 */
public final class Episode {

    public static final int SCHEMA_VER = 1;

    public long id;
    public long tStart;
    public long tEnd;
    public long dwellMs;

    /** 场景身份：pkg|O|标题 或 pkg|A|activity */
    public String sceneKey = "";
    public String pkg = "";
    public String activity = "";
    public String title = "";
    /** node | ocr | none */
    public String titleSrc = "none";
    public float titleConf;
    public float sceneConf;

    /** 证据 */
    public String framePath;
    public String titlebarPath;
    public String textSnippet = "";

    /** 该段内画面变化次数 */
    public int framesCount;

    /** event | tick | manual */
    public String mode = "event";
    /** 命中的采集策略名 */
    public String rule = "default";
    /** 目标应用版本号（换版时可解释行为变化） */
    public String appVersion = "";

    public int schemaVer = SCHEMA_VER;

    /** 是否有可用的画面证据 */
    public boolean hasFrame() { return framePath != null && !framePath.isEmpty(); }
}
