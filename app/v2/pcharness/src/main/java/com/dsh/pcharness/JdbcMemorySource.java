package com.dsh.pcharness;

import com.dsh.memory.DayRecall;
import com.dsh.memory.MemItem;
import com.dsh.memory.MemorySource;
import com.dsh.memory.Query;
import com.dsh.memory.RecallQuery;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/** 桌面 memory.db 的 MemorySource。 */
public final class JdbcMemorySource implements MemorySource {

    private final String db;
    private List<MemItem> allCache;

    public JdbcMemorySource(String db) {
        this.db = db;
    }

    private synchronized List<MemItem> all() {
        if (allCache == null) {
            try {
                JdbcMemoryStore store = new JdbcMemoryStore(db);
                allCache = store.query(new Query().includeSuperseded(false).limit(10000));
                store.close();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return allCache;
    }

    @Override public List<MemItem> candidates(RecallQuery q) {
        String ns = q == null ? null : q.namespace;
        List<MemItem> out = new java.util.ArrayList<>();
        for (MemItem m : all()) if (ns == null || ns.equals(m.namespace)) out.add(m);
        return out;
    }

    private com.dsh.memory.IdfModel idfCache;

    @Override public com.dsh.memory.IdfModel idf() {
        if (idfCache != null) return idfCache;
        try {
            java.util.List<String> texts = new java.util.ArrayList<>();
            for (com.dsh.memory.MemItem m : all()) texts.add(m.text);
            idfCache = com.dsh.memory.IdfModel.fromTexts(texts);
            return idfCache;
        } catch (Throwable t) {
            return null;
        }
    }

    public MemItem get(long id) {
        try {
            JdbcMemoryStore store = new JdbcMemoryStore(db);
            MemItem m = store.get(id);
            store.close();
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public long remember(String text, String kind, Long when, java.util.List<String> tags,
                         String sessionId, String userMessage) throws Exception {
        JdbcMemoryStore store = new JdbcMemoryStore(db);
        store.registerNamespace("chat", true);
        store.registerNamespace("manual", true);
        long id = store.put(com.dsh.memory.MemoryWriter.remember(text, kind, when, tags,
                sessionId, 0, userMessage));
        store.close();
        idfCache = null;
        allCache = null;
        return id;
    }

    public String status() {
        java.util.List<MemItem> all = candidates(new RecallQuery().limit(10000));
        long dayStart = java.time.LocalDate.now().atStartOfDay(
                java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        int today = 0;
        for (MemItem m : all) if (m.createdAt >= dayStart) today++;
        String recent = "";
        for (int i = Math.max(0, all.size() - 3); i < all.size(); i++) {
            if (!recent.isEmpty()) recent += " … ";
            recent += com.dsh.memory.MemoryStatus.recentLine(all.get(i));
        }
        return com.dsh.memory.MemoryStatus.format(all, today, 0, 0, 0, recent);
    }

    @Override public DayRecall day(String day) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT narrative,mainlines,gaps FROM day_narratives WHERE day=?")) {
            ps.setString(1, day);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DayRecall(day, rs.getString(1), rs.getString(2), rs.getString(3));
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
