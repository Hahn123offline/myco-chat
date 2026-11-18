package com.dsh.pcharness;

import com.dsh.agent.AgentEvent;
import com.dsh.agent.AgentStore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** PC 宿主：agent_sessions/agent_events 存在 memory.db，跨进程持久。 */
public final class JdbcAgentStore implements AgentStore {

    private final String db;

    public JdbcAgentStore(String db) {
        this.db = db;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS agent_sessions("
                    + "id TEXT PRIMARY KEY,kind TEXT,created_at INTEGER,updated_at INTEGER,"
                    + "status TEXT,turn_count INTEGER NOT NULL DEFAULT 0,system_prompt TEXT,meta TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS agent_events("
                    + "session_id TEXT NOT NULL,seq INTEGER NOT NULL,type TEXT NOT NULL,"
                    + "payload TEXT,at INTEGER NOT NULL,PRIMARY KEY(session_id,seq))");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override public long append(String sessionId, String type, String payload) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            long seq;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(MAX(seq),0)+1 FROM agent_events WHERE session_id=?")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); seq = rs.getLong(1); }
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO agent_events(session_id,seq,type,payload,at) VALUES(?,?,?,?,?)")) {
                ps.setString(1, sessionId); ps.setLong(2, seq); ps.setString(3, type);
                ps.setString(4, payload == null ? "{}" : payload); ps.setLong(5, now);
                ps.executeUpdate();
            }
            c.createStatement().executeUpdate("INSERT OR IGNORE INTO agent_sessions"
                    + "(id,kind,created_at,updated_at,status,turn_count) VALUES('"
                    + sessionId.replace("'", "''") + "','chat'," + now + "," + now
                    + ",'running',0)");
            return seq;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override public List<AgentEvent> events(String sessionId) {
        List<AgentEvent> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT seq,type,payload,at FROM agent_events WHERE session_id=? ORDER BY seq")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new AgentEvent(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getLong(4)));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }
}
