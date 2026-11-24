package com.dsh.pcharness;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** v15 lines 幂等索引：历史 NULL 行、不同帧同文本、同帧重投影。 */
public class LinesIdempotencyTest {

    private Connection db() throws Exception {
        Path p = Files.createTempFile("lines", ".db");
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + p);
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE lines(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "object_key TEXT,pkg TEXT,time INTEGER,text TEXT,block_id INTEGER,"
                    + "shot_ref TEXT,line_no INTEGER,y_top INTEGER,kind TEXT,frame_id INTEGER,"
                    + "created_at INTEGER)");
            s.execute("CREATE UNIQUE INDEX idx_lines_block_frame_text "
                    + "ON lines(block_id,frame_id,line_no,text)");
            s.execute("CREATE UNIQUE INDEX idx_lines_legacy "
                    + "ON lines(block_id,line_no,text) WHERE frame_id IS NULL");
        }
        return c;
    }

    private static void insert(Connection c, String text, int block, int line, Integer frame)
            throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("INSERT OR IGNORE INTO lines(text,block_id,line_no,frame_id) VALUES("
                    + "'" + text + "'," + block + "," + line + ","
                    + (frame == null ? "NULL" : frame) + ")");
        }
    }

    private static int count(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             java.sql.ResultSet r = s.executeQuery("SELECT COUNT(*) FROM lines")) {
            r.next(); return r.getInt(1);
        }
    }

    @Test public void 历史行重投影不翻倍() throws Exception {
        Connection c = db();
        insert(c, "旧行A", 1, 0, null);
        insert(c, "旧行A", 1, 0, null);   // 第二次 project
        insert(c, "旧行B", 1, 1, null);
        insert(c, "旧行B", 1, 1, null);
        assertEquals(2, count(c));
    }

    @Test public void v16去重三行只留一行且可重复执行() throws Exception {
        Connection c = db();
        try (Statement s = c.createStatement()) {
            s.execute("DROP INDEX idx_lines_legacy");
        }
        insert(c, "重复旧行", 9, 0, null);
        insert(c, "重复旧行", 9, 0, null);
        insert(c, "重复旧行", 9, 0, null);
        assertEquals(3, count(c));
        String sql = "DELETE FROM lines WHERE frame_id IS NULL AND id NOT IN ("
                + "SELECT MIN(id) FROM lines WHERE frame_id IS NULL "
                + "GROUP BY block_id,line_no,text)";
        try (Statement s = c.createStatement()) { s.execute(sql); }
        assertEquals(1, count(c));
        try (Statement s = c.createStatement()) { s.execute(sql); }   // 幂等
        assertEquals(1, count(c));
    }

    @Test public void 不同帧同文本保留两行() throws Exception {
        Connection c = db();
        insert(c, "同一句", 2, 0, 11);
        insert(c, "同一句", 2, 0, 12);
        assertEquals(2, count(c));
    }

    @Test public void 同帧同文本重投影不重复() throws Exception {
        Connection c = db();
        insert(c, "同一句", 3, 0, 21);
        insert(c, "同一句", 3, 0, 21);
        assertEquals(1, count(c));
    }
}
