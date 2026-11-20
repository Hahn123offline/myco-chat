package com.dsh.pcharness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** 迁移顺序 smoke：v13 无 frame_id → 先 ALTER 再建索引；v15 重复行 → 先去重。 */
public class MigrationSmokeTest {

    private Connection open() throws Exception {
        Path p = Files.createTempFile("mig", ".db");
        return DriverManager.getConnection("jdbc:sqlite:" + p);
    }

    private static void exec(Connection c, String sql) throws Exception {
        try (Statement s = c.createStatement()) { s.execute(sql); }
    }

    private static int count(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             java.sql.ResultSet r = s.executeQuery("SELECT COUNT(*) FROM lines")) {
            r.next(); return r.getInt(1);
        }
    }

    @Test public void v13旧库先补列再建索引不抛异常() throws Exception {
        Connection c = open();
        exec(c, "CREATE TABLE lines(id INTEGER PRIMARY KEY AUTOINCREMENT, object_key TEXT,"
                + "pkg TEXT,time INTEGER,text TEXT,block_id INTEGER,shot_ref TEXT,"
                + "line_no INTEGER,y_top INTEGER,created_at INTEGER)");
        exec(c, "INSERT INTO lines(text,block_id,line_no) VALUES('旧行',1,0)");
        // 模拟 v13 -> v16：先补列（这时还没有 frame_id）
        exec(c, "ALTER TABLE lines ADD COLUMN kind TEXT");
        exec(c, "ALTER TABLE lines ADD COLUMN frame_id INTEGER");
        // 再建索引
        exec(c, "CREATE UNIQUE INDEX idx_lines_block_frame_text "
                + "ON lines(block_id,frame_id,line_no,text)");
        exec(c, "CREATE UNIQUE INDEX idx_lines_legacy "
                + "ON lines(block_id,line_no,text) WHERE frame_id IS NULL");
        assertEquals(1, count(c));
    }

    @Test public void v15重复历史行先去重再建索引() throws Exception {
        Connection c = open();
        exec(c, "CREATE TABLE lines(id INTEGER PRIMARY KEY AUTOINCREMENT, object_key TEXT,"
                + "pkg TEXT,time INTEGER,text TEXT,block_id INTEGER,shot_ref TEXT,"
                + "line_no INTEGER,y_top INTEGER,kind TEXT,frame_id INTEGER,created_at INTEGER)");
        exec(c, "INSERT INTO lines(text,block_id,line_no,frame_id) VALUES('重复',1,0,NULL)");
        exec(c, "INSERT INTO lines(text,block_id,line_no,frame_id) VALUES('重复',1,0,NULL)");
        exec(c, "INSERT INTO lines(text,block_id,line_no,frame_id) VALUES('重复',1,0,NULL)");
        exec(c, "DELETE FROM lines WHERE frame_id IS NULL AND id NOT IN ("
                + "SELECT MIN(id) FROM lines WHERE frame_id IS NULL "
                + "GROUP BY block_id,line_no,text)");
        assertEquals(1, count(c));
        exec(c, "CREATE UNIQUE INDEX idx_lines_legacy "
                + "ON lines(block_id,line_no,text) WHERE frame_id IS NULL");
        assertEquals(1, count(c));
    }

    @Test public void 建索引失败本身不应抛到调用方() {
        // 迁移外层必须 catch；这里只验证 JDBC 层异常可被捕获，不会被吞掉
        try {
            Connection c = open();
            exec(c, "CREATE TABLE x(a TEXT)");
            exec(c, "CREATE UNIQUE INDEX ix ON x(no_such_col)");
            org.junit.Assert.fail("expected SQL failure");
        } catch (Exception expected) {
            assertTrue(expected.getMessage() != null);
        }
    }
}
