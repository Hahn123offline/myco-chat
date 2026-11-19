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

    @Override public com.dsh.memory.FrameRef readFrame(long blockId) {
        return com.dsh.memory.FrameRef.blocked("PC 宿主没有掩码图管道，拒绝看图");
    }

    @Override public int traceCount(String from, String to) {
        try {
            long a = from == null || from.isEmpty() ? 0 : parseMs(from);
            long b = to == null || to.isEmpty() ? Long.MAX_VALUE : parseMs(to);
            java.io.File ev = new java.io.File(new java.io.File(db).getParentFile(),
                    "evidence.db");
            if (!ev.exists()) return 0;
            try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                    "jdbc:sqlite:" + ev);
                 java.sql.PreparedStatement ps = c.prepareStatement(
                         "SELECT COUNT(*) FROM traces WHERE t1>? AND t0<?")) {
                ps.setLong(1, a); ps.setLong(2, b);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (Throwable t) { return 0; }
    }

    @Override public java.util.List<com.dsh.memory.LineItem> lines(
            com.dsh.memory.RecallQuery q) {
        java.util.List<com.dsh.memory.LineItem> out = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id,object_key,pkg,time,text,block_id,"
                + "shot_ref,line_no,y_top,kind FROM lines WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (q != null && q.object != null && !q.object.isEmpty()) {
            sql.append(" AND (object_key=? OR object_key LIKE ?)");
            args.add(q.object); args.add("%" + q.object + "%");
        }
        if (q != null && q.pkg != null && !q.pkg.isEmpty()) {
            sql.append(" AND (pkg=? OR pkg LIKE ?)");
            args.add(q.pkg);
            args.add("%" + q.pkg + "%");
        }
        if (q != null && q.from != null && !q.from.isEmpty()) {
            sql.append(" AND time>=?");
            args.add(parseMs(q.from));
        }
        if (q != null && q.to != null && !q.to.isEmpty()) {
            sql.append(" AND time<?");
            args.add(hasTime(q.to) ? parseMs(q.to) : parseMs(q.to) + 86400000L);
        }
        sql.append(" ORDER BY time LIMIT 5000");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db);
             java.sql.PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.dsh.memory.LineItem l = new com.dsh.memory.LineItem();
                    l.id = rs.getLong(1); l.objectKey = rs.getString(2); l.pkg = rs.getString(3);
                    l.time = rs.getLong(4); l.text = rs.getString(5); l.blockId = rs.getLong(6);
                    l.shotRef = rs.getString(7); l.lineNo = rs.getInt(8); l.yTop = rs.getInt(9);
                    l.kind = rs.getString(10) == null ? "CONTENT" : rs.getString(10);
                    out.add(l);
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static boolean hasTime(String s) {
        return s != null && s.matches(".*\\d{1,2}:\\d{2}.*");
    }

    private static long parseMs(String s) {
        try {
            String t = s.trim().replace(' ', 'T');
            if (t.length() > 10) {
                return java.time.LocalDateTime.parse(t.substring(0, 16))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            return java.time.LocalDate.parse(t).atStartOfDay(
                    java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Throwable t) { return 0; }
    }

    @Override public byte[] readShot(String shotRef) {
        try {
            java.io.File f = new java.io.File(new java.io.File(db).getParentFile(), shotRef);
            if (!f.exists()) return null;
            return java.nio.file.Files.readAllBytes(f.toPath());
        } catch (Throwable t) { return null; }
    }

    @Override public com.dsh.memory.BlockRef readBlock(long id) {
        try {
            java.io.File ev = new java.io.File(new java.io.File(db).getParentFile(),
                    "evidence.db");
            if (!ev.exists()) return null;
            try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                    "jdbc:sqlite:" + ev);
                 java.sql.PreparedStatement ps = c.prepareStatement(
                         "SELECT id,t0,t1,pkg,text,masked_text,shot_ref,page_id "
                                 + "FROM traces WHERE id=?")) {
                ps.setLong(1, id);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    com.dsh.memory.BlockRef r = new com.dsh.memory.BlockRef();
                    r.id = rs.getLong(1); r.t0 = rs.getLong(2); r.t1 = rs.getLong(3);
                    r.pkg = rs.getString(4); r.text = rs.getString(5);
                    r.maskedText = rs.getString(6); r.shotRef = rs.getString(7);
                    r.pageId = rs.getString(8);
                    return r;
                }
            }
        } catch (Throwable t) { return null; }
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
