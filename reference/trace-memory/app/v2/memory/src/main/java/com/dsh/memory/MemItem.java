package com.dsh.memory;

import java.util.ArrayList;
import java.util.List;

public final class MemItem {
    public long id;
    public String namespace = "";
    public Kind kind = Kind.BLOCK;
    public String key;
    public String text = "";
    public String data = "";
    public DetailLevel detailLevel = DetailLevel.FULL;
    public double weight = 1.0;
    public int recallCount;
    public long lastRecalledAt;
    public long t0;
    public long t1;
    public long createdAt;
    public long updatedAt;
    public List<Evidence> evidence = new ArrayList<>();
    public boolean superseded;

    public MemItem() { }

    public MemItem(String namespace, Kind kind, String key, String text, String data) {
        this.namespace = namespace == null ? "" : namespace;
        this.kind = kind == null ? Kind.BLOCK : kind;
        this.key = key;
        this.text = text == null ? "" : text;
        this.data = data == null ? "" : data;
    }
}
