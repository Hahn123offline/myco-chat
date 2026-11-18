package com.dsh.pcharness;

import com.dsh.memory.AbstractMemoryStore;
import com.dsh.memory.Evidence;
import com.dsh.memory.MemItem;
import com.dsh.memory.MemoryCodec;
import com.dsh.memory.MemorySchema;
import com.dsh.memory.Kind;
import com.dsh.memory.Query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** 桌面 SQLite store，SQL 来自 :memory 的 MemorySchema。 */
public final class JdbcMemoryStore extends AbstractMemoryStore implements AutoCloseable {

    private final Connection c;

    public JdbcMemoryStore(String path) throws Exception {
        c = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = c.createStatement()) {
            s.execute(MemorySchema.CREATE_TABLE);
            s.execute(MemorySchema.CREATE_UNIQUE_KEY);
            s.execute(MemorySchema.CREATE_INDEX_NS);
            try { s.execute(MemorySchema.ALTER_DETAIL); } catch (Exception ignored) { }
        }
        // 每次打开都注册已知 namespace（AbstractMemoryStore 的注册表是进程内实例态）
        registerNamespace("trace", true);
        registerNamespace("manual", true);
        registerNamespace("chat", true);
        registerNamespace("day", true);
        registerNamespace("event", true);
        registerNamespace("compaction", false);
    }

    public void close() throws Exception { c.close(); }

    @Override protected MemItem findKey(String namespace, String key) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM mem_items WHERE namespace=? AND key=? AND superseded=0 LIMIT 1")) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override protected long insertRow(MemItem m) {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO mem_items(namespace,kind,key,text,data,detail_level,weight,recall_count,"
                        + "last_recalled_at,t0,t1,created_at,updated_at,evidence,superseded) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            bind(ps, m, false);
            ps.executeUpdate();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                long id = rs.next() ? rs.getLong(1) : -1;
                m.id = id;
                return id;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override protected void updateRow(MemItem m) {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE mem_items SET key=?,kind=?,text=?,data=?,detail_level=?,weight=?,recall_count=?,"
                        + "last_recalled_at=?,t0=?,t1=?,updated_at=?,evidence=?,superseded=? "
                        + "WHERE id=?")) {
            ps.setString(1, m.key);
            ps.setString(2, m.kind == null ? Kind.BLOCK.name() : m.kind.name());
            ps.setString(3, m.text);
            ps.setString(4, m.data);
            ps.setString(5, m.detailLevel == null ? "FULL" : m.detailLevel.name());
            ps.setDouble(6, m.weight);
            ps.setInt(7, m.recallCount);
            ps.setLong(8, m.lastRecalledAt);
            ps.setLong(9, m.t0);
            ps.setLong(10, m.t1);
            ps.setLong(11, m.updatedAt);
            ps.setString(12, MemoryCodec.evidenceJson(m.evidence));
            ps.setInt(13, m.superseded ? 1 : 0);
            ps.setLong(14, m.id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override public MemItem get(long id) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM mem_items WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override public List<MemItem> query(Query q) {
        StringBuilder sql = new StringBuilder("SELECT * FROM mem_items WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null) {
            if (!q.includeSuperseded) sql.append(" AND superseded=0");
            if (q.namespace != null) { sql.append(" AND namespace=?"); args.add(q.namespace); }
            if (q.kind != null) { sql.append(" AND kind=?"); args.add(q.kind.name()); }
            if (q.from != null) { sql.append(" AND t0>=?"); args.add(q.from); }
            if (q.to != null) { sql.append(" AND t0<=?"); args.add(q.to); }
            sql.append(" ORDER BY t0,id LIMIT ").append(q.limit <= 0 ? 100 : q.limit);
        } else {
            sql.append(" AND superseded=0 ORDER BY t0,id LIMIT 100");
        }
        List<MemItem> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private static void bind(PreparedStatement ps, MemItem m, boolean withId) throws Exception {
        int i = 1;
        if (withId) ps.setLong(i++, m.id);
        ps.setString(i++, m.namespace);
        ps.setString(i++, m.kind == null ? Kind.BLOCK.name() : m.kind.name());
        ps.setString(i++, m.key);
        ps.setString(i++, m.text);
        ps.setString(i++, m.data);
        ps.setString(i++, m.detailLevel == null ? "FULL" : m.detailLevel.name());
        ps.setDouble(i++, m.weight);
        ps.setInt(i++, m.recallCount);
        ps.setLong(i++, m.lastRecalledAt);
        ps.setLong(i++, m.t0);
        ps.setLong(i++, m.t1);
        ps.setLong(i++, m.createdAt);
        ps.setLong(i++, m.updatedAt);
        ps.setString(i++, MemoryCodec.evidenceJson(m.evidence));
        ps.setInt(i, m.superseded ? 1 : 0);
    }

    private static MemItem row(ResultSet rs) throws Exception {
        MemItem m = new MemItem();
        m.id = rs.getLong("id");
        m.namespace = rs.getString("namespace");
        m.kind = Kind.valueOf(rs.getString("kind"));
        m.key = rs.getString("key");
        m.text = rs.getString("text");
        m.data = rs.getString("data");
        try { m.detailLevel = com.dsh.memory.DetailLevel.valueOf(
                rs.getString("detail_level").toUpperCase()); }
        catch (Throwable ignored) { m.detailLevel = com.dsh.memory.DetailLevel.FULL; }
        m.weight = rs.getDouble("weight");
        m.recallCount = rs.getInt("recall_count");
        m.lastRecalledAt = rs.getLong("last_recalled_at");
        m.t0 = rs.getLong("t0");
        m.t1 = rs.getLong("t1");
        m.createdAt = rs.getLong("created_at");
        m.updatedAt = rs.getLong("updated_at");
        m.evidence = MemoryCodec.parseEvidence(rs.getString("evidence"));
        m.superseded = rs.getInt("superseded") != 0;
        return m;
    }
}
