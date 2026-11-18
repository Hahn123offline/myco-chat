package com.dsh.usagetrace;

/** 场景解析结果。conf 表示"这个身份有多可信"，下游据此判断能不能当依据。 */
public final class Scene {

    public final String pkg;
    public final String activity;
    public final String title;
    /** node | ocr | none */
    public final String titleSrc;
    public final String key;
    public final float conf;

    public Scene(String pkg, String activity, String title, String titleSrc, String key, float conf) {
        this.pkg = pkg;
        this.activity = activity;
        this.title = title;
        this.titleSrc = titleSrc;
        this.key = key;
        this.conf = conf;
    }

    @Override
    public String toString() {
        return key + "(" + titleSrc + "," + conf + ")";
    }
}
