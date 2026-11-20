package com.dsh.memory;

/** 行级观察：文本 + 指针，不存图。 */
public final class LineItem {
    public long id;
    public String objectKey = "";
    public String pkg = "";
    public long time;
    public String text = "";
    public long blockId;
    public long frameId;
    public String shotRef = "";
    public int lineNo;
    public int yTop;
    public int left, right;
    public double score;
    public String kind = "CONTENT";
    public String match = "exact";
}
