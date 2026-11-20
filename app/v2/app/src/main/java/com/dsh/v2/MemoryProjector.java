package com.dsh.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dsh.models.Json;

/** 单向投影：记忆库内部搬运（2a: chains → mem_items event）。 */
public final class MemoryProjector {

    private MemoryProjector() { }

    /** 3：day_narratives → namespace=day, kind=DAY；不依赖证据库，幂等。 */
    public static int projectDays(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        int n = 0;
        try (Cursor c = db.rawQuery("SELECT day,narrative,mainlines,open_threads,gaps,"
                + "repaired,index_partial FROM day_narratives", null)) {
            while (c.moveToNext()) {
                String day = c.getString(0);
                if (day == null || day.isEmpty()) continue;
                String mainlines = c.getString(2);
                String summary;
                if (mainlines != null && mainlines.length() > 2) {
                    summary = mainlines;
                } else {
                    String narr = c.getString(1);
                    summary = narr == null ? "" : narr;
                }
                if (summary.length() > 800) summary = summary.substring(0, 800);
                long t0;
                try { t0 = java.time.LocalDate.parse(day).atStartOfDay(
                        java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); }
                catch (Throwable t) { t0 = System.currentTimeMillis(); }
                long t1 = t0 + 86_400_000L - 1;
                ContentValues v = new ContentValues();
                v.put("namespace", "day");
                v.put("kind", "DAY");
                v.put("key", "day:" + day);
                v.put("text", summary);
                v.put("data", "{\"narrative\":" + Json.quote(c.getString(1))
                        + ",\"mainlines\":" + safeJson(mainlines)
                        + ",\"open_threads\":" + safeJson(c.getString(3))
                        + ",\"gaps\":" + safeJson(c.getString(4))
                        + ",\"repaired\":" + (c.getInt(5) != 0)
                        + ",\"index_partial\":" + c.getInt(6) + "}");
                v.put("detail_level", "FULL");
                v.put("weight", 1.0);
                v.put("recall_count", 0);
                v.put("last_recalled_at", 0);
                v.put("t0", t0);
                v.put("t1", t1);
                v.put("created_at", System.currentTimeMillis());
                v.put("updated_at", System.currentTimeMillis());
                v.put("evidence", "[{\"type\":\"trace\",\"ref\":\"day:" + day
                        + "\",\"t\":" + t0 + "}]");
                v.put("superseded", 0);
                long r = db.insertWithOnConflict("mem_items", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (r >= 0) n++;
            }
        }
        android.util.Log.i("DSHL2", "project days: " + n);
        return n;
    }


    /** 行视图：evidence.traces/page_elements → lines（文本+指针+kind；全量分批，幂等）。 */
    public static synchronized int projectLines(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        java.io.File ev = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                "evidence.db");
        if (!ev.exists()) return 0;
        long maxBlock = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(block_id),0) FROM lines", null)) {
            if (c.moveToFirst()) maxBlock = c.getLong(0);
        }
        SQLiteDatabase edb = null;
        int n = 0, traces = 0, framesTotal = 0;
        android.util.Log.i("DSHL2", "lines project start maxBlock=" + maxBlock);
        try {
            edb = SQLiteDatabase.openDatabase(ev.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            try (Cursor t = edb.rawQuery("SELECT id,t0,pkg,page_title,object_key,activity,shot_ref "
                    + "FROM traces WHERE id>? AND shot_ref IS NOT NULL AND shot_ref!='' "
                    + "ORDER BY id", new String[]{String.valueOf(maxBlock)})) {
                while (t.moveToNext()) {
                    traces++;
                    long id = t.getLong(0);
                    long time = t.getLong(1);
                    String pkg = t.getString(2);
                    String title = t.getString(3);
                    String ok = t.getString(4);
                    String activity = t.getString(5);
                    String shot = t.getString(6);
                    java.util.List<String> texts = new java.util.ArrayList<>();
                    java.util.List<Integer> tops = new java.util.ArrayList<>();
                    java.util.List<String> srcs = new java.util.ArrayList<>();
                    try (Cursor e = edb.rawQuery(
                            "SELECT text,top,source FROM page_elements WHERE shot_ref=? "
                                    + "ORDER BY top,left", new String[]{shot})) {
                        while (e.moveToNext()) {
                            String tx = e.getString(0);
                            if (tx == null || tx.trim().isEmpty()) continue;
                            String src = e.getString(2);
                            if (!"A11Y".equals(src) && !"BOTH".equals(src)
                                    && !"OCR".equals(src)) continue;
                            texts.add(tx.trim()); tops.add(e.getInt(1)); srcs.add(src);
                        }
                    }
                    if (texts.isEmpty()) continue;
                    if (ok == null || ok.isEmpty()) {
                        if ((pkg == null ? "" : pkg).length() > 0 && title != null && !title.isEmpty()) {
                            ok = pkg + "/" + title;
                        }
                    }
                    if (ok == null || ok.isEmpty()) ok = activityFallback(pkg, activity);
                    if (ok == null || ok.isEmpty()) ok = firstLineFallback(pkg, texts);
                    if (ok == null) ok = "";
                    String objTitle = ok.contains("/") ? ok.substring(ok.indexOf('/') + 1) : "";
                    db.beginTransaction();
                    try {
                        // 多帧：按 frame 的 shot_ref 读该帧 page_elements，逐元素落行（带真实 bbox）
                        int framesThisTrace = 0;
                        try (Cursor fr = edb.rawQuery("SELECT id,shot_ref,text FROM trace_frames "
                                + "WHERE trace_id=? ORDER BY seq", new String[]{String.valueOf(id)})) {
                            while (fr.moveToNext()) {
                                long frameId = fr.getLong(0);
                                String fshot = fr.getString(1);
                                String ftext = fr.getString(2);
                                framesThisTrace++;
                                int fno = 0;
                                boolean hadElement = false;
                                if (fshot != null && !fshot.isEmpty()) {
                                    try (Cursor fe = edb.rawQuery(
                                            "SELECT text,top,source FROM page_elements WHERE shot_ref=? "
                                                    + "ORDER BY top,left", new String[]{fshot})) {
                                        while (fe.moveToNext()) {
                                            String tx = fe.getString(0);
                                            if (tx == null || tx.trim().isEmpty()) continue;
                                            String src = fe.getString(2);
                                            if (!"A11Y".equals(src) && !"BOTH".equals(src)
                                                    && !"OCR".equals(src)) continue;
                                            int top = fe.getInt(1);
                                            ContentValues fv = new ContentValues();
                                            fv.put("object_key", ok); fv.put("pkg", pkg);
                                            fv.put("time", time); fv.put("text", tx.trim());
                                            fv.put("block_id", id); fv.put("shot_ref", fshot);
                                            fv.put("line_no", fno++);
                                            fv.put("y_top", top);
                                            fv.put("kind", classify(tx.trim(), top, objTitle));
                                            fv.put("frame_id", frameId);
                                            fv.put("created_at", System.currentTimeMillis());
                                            long r = db.insertWithOnConflict("lines", null, fv,
                                                    SQLiteDatabase.CONFLICT_IGNORE);
                                            if (r >= 0) n++;
                                            hadElement = true;
                                        }
                                    } catch (Throwable ignored) { }
                                }
                                // 无图/无元素时：文本行不带 bbox（不伪造 y=500），不可声称版式
                                if (!hadElement && ftext != null && !ftext.trim().isEmpty()) {
                                    ContentValues fv = new ContentValues();
                                    fv.put("object_key", ok); fv.put("pkg", pkg);
                                    fv.put("time", time); fv.put("text", ftext.trim());
                                    fv.put("block_id", id); fv.put("shot_ref", (String) null);
                                    fv.putNull("y_top");
                                    fv.put("line_no", 0);
                                    fv.put("kind", "CHROME");
                                    fv.put("frame_id", frameId);
                                    fv.put("created_at", System.currentTimeMillis());
                                    long r = db.insertWithOnConflict("lines", null, fv,
                                            SQLiteDatabase.CONFLICT_IGNORE);
                                    if (r >= 0) n++;
                                }
                            }
                        } catch (Throwable ignored) { }
                        framesTotal += framesThisTrace;
                        for (int i = 0; i < texts.size(); i++) {
                            String text = texts.get(i);
                            ContentValues v = new ContentValues();
                            v.put("object_key", ok);
                            v.put("pkg", pkg);
                            v.put("time", time);
                            v.put("text", text);
                            v.put("block_id", id);
                            v.put("shot_ref", shot);
                            v.put("line_no", i);
                            v.put("y_top", tops.get(i));
                            v.put("kind", classify(text, tops.get(i), objTitle));
                            v.put("created_at", System.currentTimeMillis());
                            long r = db.insertWithOnConflict("lines", null, v,
                                    SQLiteDatabase.CONFLICT_IGNORE);
                            if (r >= 0) n++;
                        }
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                    if (traces % 200 == 0) {
                        android.util.Log.i("DSHL2", "lines project: traces=" + traces
                                + " lines=" + n);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "project lines failed", t);
        } finally {
            if (edb != null) try { edb.close(); } catch (Throwable ignored) { }
        }
        backfillKinds(app, db);
        markFrequencyChrome(db);
        long toBlock = maxBlock;
        try (Cursor mb = db.rawQuery("SELECT COALESCE(MAX(block_id),0) FROM lines", null)) {
            if (mb.moveToFirst()) toBlock = mb.getLong(0);
        }
        ProjectionHealth.lastProjectAt = System.currentTimeMillis();
        ProjectionHealth.lastBlock = toBlock;
        ProjectionHealth.lastLineBlock = toBlock;
        ProjectionHealth.lastTraces = traces;
        ProjectionHealth.lastFrames = framesTotal;
        android.util.Log.i("DSHL2", "lines project inc: from_block=" + (maxBlock + 1)
                + " to_block=" + toBlock + " traces=" + traces
                + " frames=" + framesTotal + " lines=" + n);
        try (Cursor lc = db.rawQuery("SELECT COUNT(*) FROM lines WHERE frame_id IS NULL", null)) {
            int legacy = lc.moveToFirst() ? lc.getInt(0) : 0;
            try (Cursor fc = db.rawQuery("SELECT COUNT(*) FROM lines WHERE frame_id IS NOT NULL", null)) {
                int framed = fc.moveToFirst() ? fc.getInt(0) : 0;
                android.util.Log.i("DSHL2", "lines project: traces=" + traces
                        + " frames=" + framesTotal + " legacy=" + legacy
                        + " framed=" + framed + " new=" + n);
            }
        } catch (Throwable t) {
            android.util.Log.i("DSHL2", "lines project: traces=" + traces
                    + " frames=" + framesTotal + " lines=" + n);
        }
        return n;
    }

    /** 行 → 对象消息（长截图记忆）：chrome 不进；跨帧去重；sender 前缀提取。 */
    public static synchronized int projectObjectMessages(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        java.util.List<com.dsh.memory.LineItem> lines = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT object_key,pkg,time,text,block_id,frame_id,"
                + "y_top,kind FROM lines WHERE kind IN ('CONTENT','SYSTEM') "
                + "AND text IS NOT NULL AND text!='' ORDER BY time", null)) {
            while (c.moveToNext()) {
                com.dsh.memory.LineItem l = new com.dsh.memory.LineItem();
                l.objectKey = c.getString(0);
                l.pkg = c.getString(1);
                l.time = c.getLong(2);
                l.text = c.getString(3);
                l.blockId = c.getLong(4);
                l.frameId = c.getLong(5);
                l.yTop = c.getInt(6);
                l.kind = c.getString(7);
                lines.add(l);
            }
        }
        java.util.List<com.dsh.memory.MessageBuilder.Msg> msgs =
                com.dsh.memory.MessageBuilder.build(lines);
        db.beginTransaction();
        try {
            db.delete("object_messages", null, null);
            for (com.dsh.memory.MessageBuilder.Msg m : msgs) {
                ContentValues v = new ContentValues();
                v.put("object_key", m.objectKey);
                v.put("pkg", m.pkg);
                v.put("t_first", m.tFirst);
                v.put("t_last", m.tLast);
                v.put("seen_count", m.seenCount);
                v.put("sender", m.sender);
                v.put("text", m.text);
                v.put("text_key", m.textKey);
                v.put("block_id", m.blockId);
                v.put("frame_id", m.frameId);
                db.insertWithOnConflict("object_messages", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        android.util.Log.i("DSHL2", "object messages: lines=" + lines.size()
                + " messages=" + msgs.size());
        return msgs.size();
    }

    private static boolean fragmented(String t) {
        int switches = 0, run = 0;
        boolean lastCjk = false, first = true;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            boolean cjk = ch >= 0x4e00 && ch <= 0x9fff;
            if (!cjk && !Character.isLetterOrDigit(ch)) continue;
            if (first) { lastCjk = cjk; first = false; run = 1; continue; }
            if (cjk == lastCjk) { run++; continue; }
            if (run <= 1) switches++;
            run = 1; lastCjk = cjk;
        }
        return switches >= 3;
    }

    /** 同 pkg 内出现于 >35% 屏的文本视为 chrome。 */
    private static void markFrequencyChrome(SQLiteDatabase db) {
        try {
            java.util.Map<String, Integer> screens = new java.util.HashMap<>();
            try (Cursor c = db.rawQuery("SELECT pkg,COUNT(DISTINCT block_id) FROM lines "
                    + "GROUP BY pkg", null)) {
                while (c.moveToNext()) screens.put(c.getString(0), c.getInt(1));
            }
            java.util.List<String[]> freq = new java.util.ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT pkg,text,COUNT(DISTINCT block_id) n "
                    + "FROM lines GROUP BY pkg,text", null)) {
                while (c.moveToNext()) {
                    String pkg = c.getString(0);
                    int total = screens.getOrDefault(pkg, 0);
                    if (total >= 3 && c.getInt(2) * 100 > total * 35) {
                        freq.add(new String[]{pkg, c.getString(1)});
                    }
                }
            }
            if (!freq.isEmpty()) {
                db.beginTransaction();
                try {
                    for (String[] x : freq) {
                        ContentValues v = new ContentValues();
                        v.put("kind", "CHROME");
                        db.update("lines", v, "pkg=? AND text=?", new String[]{x[0], x[1]});
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "freq chrome failed", t);
        }
    }

    /** 一次性回填历史行的 kind（null → 规则分类），500/批，幂等。 */
    public static int backfillKinds(Context app, SQLiteDatabase db) {
        int rows = 0, changed = 0;
        try {
            while (true) {
                java.util.List<Object[]> batch = new java.util.ArrayList<>();
                try (Cursor c = db.rawQuery("SELECT id,text,y_top,object_key FROM lines "
                        + "WHERE kind IS NULL LIMIT 500", null)) {
                    while (c.moveToNext()) {
                        batch.add(new Object[]{c.getLong(0), c.getString(1),
                                c.getInt(2), c.getString(3)});
                    }
                }
                if (batch.isEmpty()) break;
                db.beginTransaction();
                try {
                    for (Object[] x : batch) {
                        String obj = (String) x[3];
                        String title = obj != null && obj.contains("/")
                                ? obj.substring(obj.indexOf('/') + 1) : "";
                        String k = classify((String) x[1], (Integer) x[2], title);
                        ContentValues v = new ContentValues();
                        v.put("kind", k);
                        changed += db.update("lines", v, "id=? AND kind IS NULL",
                                new String[]{String.valueOf(x[0])});
                        rows++;
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                if (rows % 2000 == 0) {
                    android.util.Log.i("DSHL2", "lines backfill: rows=" + rows
                            + " changed=" + changed);
                }
            }
            android.util.Log.i("DSHL2", "lines backfill done: rows=" + rows
                    + " changed=" + changed);
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "lines backfill failed", t);
        }
        return changed;
    }

    private static String activityFallback(String pkg, String activity) {
        if (pkg == null || pkg.isEmpty() || activity == null) return "";
        int dot = activity.lastIndexOf('.');
        String a = dot >= 0 ? activity.substring(dot + 1) : activity;
        if (a.length() < 4) return "";
        if (a.contains("Splash") || a.contains("MainActivity") || a.contains("FrameLayout")
                || a.contains("Launcher") || a.contains("ComponentDialog")) return "";
        return pkg + "/" + a;
    }

    private static String firstLineFallback(String pkg, java.util.List<String> texts) {
        int multi = 0;
        for (String x : texts) if (x.contains(", ,")) multi++;
        if (multi >= 2) return "";   // 列表页一屏多对象：留空
        for (String x : texts) {
            String t = x.trim();
            if (t.length() < 4) continue;
            int cjk = 0;
            for (int i = 0; i < t.length(); i++) {
                char ch = t.charAt(i);
                if (ch >= 0x4e00 && ch <= 0x9fff) cjk++;
            }
            if (cjk < 3) continue;
            if (classify(t, 500, "").equals("CONTENT")) return (pkg == null ? "" : pkg) + "/" + t;
        }
        return "";
    }

    /** 行级 chrome 分类：位置 + 文本双判据。 */
    static String classify(String text, int y, String title) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "CHROME";
        if (y < 120 || y > 2350) return "CHROME";
        boolean asciiWord = t.matches("(?s).*[A-Za-z]{3,}.*");
        int cjk = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch >= 0x4e00 && ch <= 0x9fff) cjk++;
        }
        boolean punct = t.matches("(?s).*[，。！？、；：,.!?;:].*");
        int words = 0;
        java.util.regex.Matcher wm = java.util.regex.Pattern
                .compile("[A-Za-z]{2,}").matcher(t);
        while (wm.find()) words++;
        int symDigit = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isDigit(ch) || (!Character.isLetterOrDigit(ch)
                    && ch != ' ' && ch != '\t')) symDigit++;
        }
        if (cjk < 6 && !asciiWord) return "CHROME";
        if (t.length() < 8 && !punct) return "CHROME";
        if (!punct && t.length() < 16) return "CHROME";
        if (t.matches("(?s).*(包邮|券|拼单|秒杀|￥|¥|\\d+人在拼|立即抢购|加入购物车).*"))
            return "CHROME";
        // 乱码构成判据：不是长度，而是汉字/英文词/符号数字的构成
        if (cjk < 4 && words < 2) return "CHROME";
        if (t.length() > 0 && symDigit * 100 / t.length() > 40) return "CHROME";
        if (!punct && t.indexOf(' ') < 0 && t.length() > 20) return "CHROME";
        if (fragmented(t)) return "CHROME";
        if (!asciiWord && cjk < 2) return "CHROME";
        if (title != null && !title.isEmpty() && t.equals(title)) return "TITLE";
        if (y < 300 && (t.matches("^\\d{1,2}:\\d{2}.*")
                || t.matches(".*(5G|4G|KB/s|KB/S|%|电量|信号|运营商).*")
                || t.matches(".*(WLAN|wifi|Wi-Fi).*"))) return "STATUS";
        if (t.matches("^(返回.*|聊天设置|听筒模式|已读.*|消息未读.*|.*未读\\d*)$")) return "NAV";
        if (y > 2100 && (t.matches("^(发送|相册|语音|拍照|表情|更多功能|符号.*|换行|回)$")
                || t.matches("^[A-Z]( [A-Z])*$") || t.matches("^(中っ|L英|确定|取消)$"))) return "TOOLBAR";
        if (t.matches(".*(加入了群聊|拍了拍|上线了|撤回了一条消息).*")) return "SYSTEM";
        return "CONTENT";
    }

    private static String safeJson(String s) {
        return s == null || s.trim().isEmpty() ? "[]" : s;
    }

    /** 2b：done chains 的 key_block_ids → namespace=trace；证据缺失只计数。 */
    public static void projectTraceBlocks(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        int chains = 0;
        try (Cursor c = db.rawQuery("SELECT key_block_ids FROM chains WHERE status='done'", null)) {
            while (c.moveToNext()) {
                chains++;
                String csv = c.getString(0);
                if (csv == null) continue;
                for (String s : csv.split(",")) {
                    try { ids.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
                }
            }
        }
        int total = ids.size(), projected = 0, missing = 0;
        java.io.File ev = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                "evidence.db");
        SQLiteDatabase edb = null;
        if (ev.exists()) {
            try { edb = SQLiteDatabase.openDatabase(ev.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY); } catch (Throwable ignored) { }
        }
        if (edb == null) missing = total;
        else {
            try {
                for (long id : ids) {
                    try (Cursor t = edb.rawQuery("SELECT id,t0,t1,dwell_ms,pkg,page_id,page_title,"
                            + "text,shot_ref FROM traces WHERE id=?", new String[]{String.valueOf(id)})) {
                        if (!t.moveToFirst()) { missing++; continue; }
                        String text = t.getString(7);
                        String shot = t.getString(8);
                        String level = text != null && !text.trim().isEmpty() ? "FULL"
                                : shot != null && !shot.trim().isEmpty() ? "IMAGE_ONLY" : "APP_ONLY";
                        ContentValues v = new ContentValues();
                        v.put("namespace", "trace");
                        v.put("kind", "BLOCK");
                        v.put("key", "trace:" + id);
                        v.put("text", text == null ? "" : text);
                        v.put("data", "{\"pkg\":" + Json.quote(t.getString(4))
                                + ",\"page_id\":" + Json.quote(t.getString(5))
                                + ",\"page_title\":" + Json.quote(t.getString(6))
                                + ",\"shot_ref\":" + Json.quote(shot)
                                + ",\"dwell_ms\":" + t.getLong(3)
                                + ",\"t0\":" + t.getLong(1) + ",\"t1\":" + t.getLong(2) + "}");
                        v.put("detail_level", level);
                        v.put("weight", 1.0);
                        v.put("recall_count", 0);
                        v.put("last_recalled_at", 0);
                        v.put("t0", t.getLong(1));
                        v.put("t1", t.getLong(2));
                        v.put("created_at", System.currentTimeMillis());
                        v.put("updated_at", System.currentTimeMillis());
                        v.put("evidence", "[{\"type\":\"trace\",\"ref\":\"" + id
                                + "\",\"t\":" + t.getLong(1) + "}]");
                        v.put("superseded", 0);
                        long r = db.insertWithOnConflict("mem_items", null, v,
                                SQLiteDatabase.CONFLICT_IGNORE);
                        if (r >= 0) projected++;
                    }
                }
            } finally {
                try { edb.close(); } catch (Throwable ignored) { }
            }
        }
        android.util.Log.i("DSHL2", "project: chains=" + chains + " key_blocks_total=" + total
                + " projected=" + projected + " missing_evidence=" + missing);
    }

    /** 2a：done chains → namespace=event, kind=EPISODE, key=chain:<id>；幂等。 */
    public static int projectEvents(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        int n = 0;
        try (Cursor c = db.rawQuery("SELECT id,title,body,t0,t1,n_blocks,outcome,"
                + "key_block_ids,text_source,covered_ms FROM chains WHERE status='done'", null)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String title = c.getString(1);
                String body = c.getString(2);
                ContentValues v = new ContentValues();
                v.put("namespace", "event");
                v.put("kind", "EPISODE");
                v.put("key", "chain:" + id);
                v.put("text", (title == null ? "" : title) + "\n" + (body == null ? "" : body));
                v.put("data", "{\"t0\":" + c.getLong(3) + ",\"t1\":" + c.getLong(4)
                        + ",\"n_blocks\":" + c.getInt(5) + ",\"outcome\":"
                        + Json.quote(c.getString(6))
                        + ",\"key_block_ids\":" + Json.quote(c.getString(7))
                        + ",\"text_source\":" + Json.quote(c.getString(8))
                        + ",\"covered_ms\":" + c.getLong(9) + "}");
                v.put("detail_level", "FULL");
                v.put("weight", 1.0);
                v.put("recall_count", 0);
                v.put("last_recalled_at", 0);
                v.put("t0", c.getLong(3));
                v.put("t1", c.getLong(4));
                v.put("created_at", System.currentTimeMillis());
                v.put("updated_at", System.currentTimeMillis());
                v.put("evidence", "[{\"type\":\"trace\",\"ref\":\"chain:" + id
                        + "\",\"t\":" + c.getLong(3) + "}]");
                v.put("superseded", 0);
                long r = db.insertWithOnConflict("mem_items", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (r >= 0) n++;
                else db.update("mem_items", v, "namespace='event' AND key=?",
                        new String[]{"chain:" + id});
            }
        }
        android.util.Log.i("DSHL2", "project events: chains=" + n);
        return n;
    }
}
