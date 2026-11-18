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
public final class JdbcMemorySource implements MemorySource, com.dsh.memory.MemoryStore {

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

    private JdbcMemoryStore open() throws Exception { return new JdbcMemoryStore(db); }

    @Override public long put(com.dsh.memory.MemItem item) {
        try (JdbcMemoryStore s = open()) { return s.put(item); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public java.util.List<com.dsh.memory.MemItem> query(com.dsh.memory.Query q) {
        try (JdbcMemoryStore s = open()) { return s.query(q); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public void boost(long id, double delta, double cap) {
        try (JdbcMemoryStore s = open()) { s.boost(id, delta, cap); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public void patch(long id, java.util.Map<String, Object> p) {
        try (JdbcMemoryStore s = open()) { s.patch(id, p); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public void supersede(long id, String retiredKey,
                                    java.util.Map<String, Object> dataPatch) {
        try (JdbcMemoryStore s = open()) { s.supersede(id, retiredKey, dataPatch); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public void remove(long id) {
        try (JdbcMemoryStore s = open()) { s.remove(id); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public void registerNamespace(String ns, boolean req) {
        try (JdbcMemoryStore s = open()) { s.registerNamespace(ns, req); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override public boolean isNamespaceRegistered(String ns) {
        try (JdbcMemoryStore s = open()) { return s.isNamespaceRegistered(ns); }
        catch (Exception e) { return false; }
    }
    @Override public boolean namespaceRequiresEvidence(String ns) {
        try (JdbcMemoryStore s = open()) { return s.namespaceRequiresEvidence(ns); }
        catch (Exception e) { return false; }
    }

    @Override public com.dsh.memory.DayDiagnostic diagnose(String day) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(day);
            long from = d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long to = d.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            java.io.File mem = new java.io.File(db);
            java.io.File ev = new java.io.File(mem.getParentFile(), "evidence.db");
            int n = 0; long covered = 0;
            java.util.Map<String, Integer> pkgs = new java.util.LinkedHashMap<>();
            if (ev.exists()) {
                try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                        "jdbc:sqlite:" + ev);
                     java.sql.PreparedStatement ps = c.prepareStatement(
                             "SELECT pkg,t0,t1 FROM traces WHERE t0>=? AND t0<?")) {
                    ps.setLong(1, from); ps.setLong(2, to);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            n++;
                            covered += Math.max(0, rs.getLong(3) - rs.getLong(2));
                            String p = rs.getString(1) == null ? "(unknown)" : rs.getString(1);
                            pkgs.merge(p, 1, Integer::sum);
                        }
                    }
                }
            }
            int events = 0;
            for (MemItem m : candidates(new RecallQuery().from(day).to(day).limit(10000))) {
                if ("event".equals(m.namespace)) events++;
            }
            return new com.dsh.memory.DayDiagnostic(day, n, covered, pkgs, events, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public boolean evidenceAvailable(String shotRef) {
        if (shotRef == null || shotRef.isEmpty()) return true;
        java.io.File mem = new java.io.File(db);
        java.io.File ev = new java.io.File(mem.getParentFile(), "evidence.db");
        return ev.exists();
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
