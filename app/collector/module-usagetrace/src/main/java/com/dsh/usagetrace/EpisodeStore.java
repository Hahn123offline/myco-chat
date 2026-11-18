package com.dsh.usagetrace;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.dsh.host.api.data.Episode;
import com.dsh.host.api.trace.EpisodeStats;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 注意轨迹的持久化。只追加，不改历史（除段落关闭时落定 tEnd）。
 * schema 带版本，便于未来迁移。
 *
 * 实现 {@link EpisodeStats}（host-api 只读统计接口），供记忆模块/状态页做
 * "episodes 与 blocks 条数对账" —— 采集到了多少条、记忆只落了多少条，必须能被看见。
 */
public final class EpisodeStore extends SQLiteOpenHelper implements EpisodeStats {

    private static final String NAME = "usagetrace.db";
    private static final int DB_VER = 1;

    public EpisodeStore(Context c) {
        super(c, pathOf(c), null, DB_VER);
    }

    private static String pathOf(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        if (!d.exists()) d.mkdirs();
        File f = new File(d, "trace");
        if (!f.exists()) f.mkdirs();
        return new File(f, NAME).getAbsolutePath();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE episodes(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "t_start INTEGER NOT NULL, t_end INTEGER, dwell_ms INTEGER," +
                "scene_key TEXT, pkg TEXT, activity TEXT," +
                "title TEXT, title_src TEXT, title_conf REAL, scene_conf REAL," +
                "frame_path TEXT, titlebar_path TEXT, text_snippet TEXT," +
                "frames_count INTEGER, mode TEXT, rule TEXT, app_version TEXT," +
                "schema_ver INTEGER)");
        db.execSQL("CREATE INDEX idx_start ON episodes(t_start)");
        db.execSQL("CREATE INDEX idx_pkg ON episodes(pkg, t_start)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 未来迁移从这里走；当前只有 v1
    }

    public long insertOpen(Episode e) {
        ContentValues v = new ContentValues();
        v.put("t_start", e.tStart);
        v.put("t_end", e.tEnd);
        v.put("dwell_ms", e.dwellMs);
        v.put("scene_key", e.sceneKey);
        v.put("pkg", e.pkg);
        v.put("activity", e.activity);
        v.put("title", e.title);
        v.put("title_src", e.titleSrc);
        v.put("title_conf", e.titleConf);
        v.put("scene_conf", e.sceneConf);
        v.put("frame_path", e.framePath);
        v.put("titlebar_path", e.titlebarPath);
        v.put("text_snippet", e.textSnippet);
        v.put("frames_count", e.framesCount);
        v.put("mode", e.mode);
        v.put("rule", e.rule);
        v.put("app_version", e.appVersion);
        v.put("schema_ver", e.schemaVer);
        long id = getWritableDatabase().insert("episodes", null, v);
        e.id = id;
        return id;
    }

    /** 段落关闭：落定结束时间与最终证据 */
    public void closeEpisode(Episode e) {
        if (e.id <= 0) { insertOpen(e); return; }
        ContentValues v = new ContentValues();
        v.put("t_end", e.tEnd);
        v.put("dwell_ms", e.dwellMs);
        v.put("title", e.title);
        v.put("title_src", e.titleSrc);
        v.put("title_conf", e.titleConf);
        v.put("scene_conf", e.sceneConf);
        v.put("frame_path", e.framePath);
        v.put("titlebar_path", e.titlebarPath);
        v.put("text_snippet", e.textSnippet);
        v.put("frames_count", e.framesCount);
        getWritableDatabase().update("episodes", v, "id=?", new String[]{String.valueOf(e.id)});
    }

    @Override
    public long countTotal() {
        Cursor cur = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM episodes", null);
        long n = cur.moveToFirst() ? cur.getLong(0) : 0;
        cur.close();
        return n;
    }

    @Override
    public long countToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Cursor cur = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM episodes WHERE t_start>=?",
                new String[]{String.valueOf(c.getTimeInMillis())});
        long n = cur.moveToFirst() ? cur.getLong(0) : 0;
        cur.close();
        return n;
    }

    public List<Episode> recent(int n) {
        return query("SELECT * FROM episodes ORDER BY id DESC LIMIT " + Math.max(1, n));
    }

    public List<Episode> between(long from, long to) {
        return query("SELECT * FROM episodes WHERE t_start>=? AND t_start<=? ORDER BY t_start",
                String.valueOf(from), String.valueOf(to));
    }

    private List<Episode> query(String sql, String... args) {
        List<Episode> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(read(c));
        c.close();
        return out;
    }

    private Episode read(Cursor c) {
        Episode e = new Episode();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.tStart = c.getLong(c.getColumnIndexOrThrow("t_start"));
        e.tEnd = c.getLong(c.getColumnIndexOrThrow("t_end"));
        e.dwellMs = c.getLong(c.getColumnIndexOrThrow("dwell_ms"));
        e.sceneKey = str(c, "scene_key");
        e.pkg = str(c, "pkg");
        e.activity = str(c, "activity");
        e.title = str(c, "title");
        e.titleSrc = str(c, "title_src");
        e.titleConf = (float) c.getDouble(c.getColumnIndexOrThrow("title_conf"));
        e.sceneConf = (float) c.getDouble(c.getColumnIndexOrThrow("scene_conf"));
        e.framePath = str(c, "frame_path");
        e.titlebarPath = str(c, "titlebar_path");
        e.textSnippet = str(c, "text_snippet");
        e.framesCount = c.getInt(c.getColumnIndexOrThrow("frames_count"));
        e.mode = str(c, "mode");
        e.rule = str(c, "rule");
        e.appVersion = str(c, "app_version");
        e.schemaVer = c.getInt(c.getColumnIndexOrThrow("schema_ver"));
        return e;
    }

    private String str(Cursor c, String col) {
        int i = c.getColumnIndexOrThrow(col);
        return c.isNull(i) ? "" : c.getString(i);
    }
}
