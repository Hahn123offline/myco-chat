package com.dsh.pcharness;

import com.dsh.memory.LineItem;
import com.dsh.memory.MessageBuilder;
import com.dsh.memory.TitlePicker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PC 离线重建 lines/object_messages；与 Android 共用 TitlePicker/MessageBuilder。
 * 迭代用法：--last-n 50 --object 零杯；全量只在收尾跑。 */
public final class OfflineProjection {

    public static final class Elem {
        public String text, source, role, pkg;
        public int left, top, right, bottom;
    }

    private OfflineProjection() { }

    public static void run(String[] args) throws Exception {
        String ev = arg(args, "--evidence", "raw/v2/device/ev.db");
        String out = arg(args, "--memory", "raw/v2/device/new_mem.db");
        int lastN = Integer.parseInt(arg(args, "--last-n", "0"));
        long since = Long.parseLong(arg(args, "--since", "0"));
        String object = arg(args, "--object", "");
        long t0 = System.currentTimeMillis();
        Class.forName("org.sqlite.JDBC");
        try (Connection e = DriverManager.getConnection("jdbc:sqlite:" + ev);
             Connection m = DriverManager.getConnection("jdbc:sqlite:" + out)) {
            init(m);
            List<Object[]> traces = loadTraces(e, lastN, since, object);
            Map<Long, List<Object[]>> frames = loadFrames(e, traces);
            Set<String> shots = new HashSet<>();
            for (Object[] t : traces) if (t[5] != null) shots.add((String) t[5]);
            for (List<Object[]> fs : frames.values())
                for (Object[] f : fs) if (f[3] != null) shots.add((String) f[3]);
            Map<String, List<Elem>> elems = loadElements(e, shots);
            long totalLines = 0, xOk = 0, frameOk = 0, frameTotal = 0;
            List<LineItem> allLines = new ArrayList<>();
            try (PreparedStatement ps = m.prepareStatement("INSERT OR IGNORE INTO lines("
                    + "object_key,pkg,time,text,block_id,shot_ref,line_no,y_top,kind,frame_id,"
                    + "x_left,x_right,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (Object[] t : traces) {
                    long tid = (Long) t[0]; long time = (Long) t[1];
                    String pkg = nz((String) t[2]); String baseOk = nz((String) t[4]);
                    String shot = (String) t[5];
                    List<Object[]> fs = frames.getOrDefault(tid, new ArrayList<>());
                    if (fs.isEmpty()) fs.add(new Object[]{0L, 0L, time, shot, "", baseOk});
                    for (Object[] f : fs) {
                        long fid = (Long) f[0]; long ft = (Long) f[2];
                        String fshot = (String) f[3]; String ftext = nz((String) f[4]);
                        List<Elem> es = elems.getOrDefault(fshot, new ArrayList<>());
                        String framePkg = pkg;
                        if (!es.isEmpty() && es.get(0).pkg != null && !es.get(0).pkg.isEmpty())
                            framePkg = es.get(0).pkg;
                        String obj = "";
                        if (framePkg.equals(pkg)) {
                            List<TitlePicker.Candidate> cs = new ArrayList<>();
                            for (Elem x : es) cs.add(new TitlePicker.Candidate(x.text,
                                    x.top, x.source, x.role));
                            String picked = TitlePicker.pick(cs);
                            obj = picked.isEmpty() ? baseOk : pkg + "/" + picked;
                            if (!picked.isEmpty()) frameOk++;
                            frameTotal++;
                        }
                        if (!es.isEmpty()) {
                            int lineNo = 0;
                            for (Elem x : es) {
                                if (x.text == null || x.text.trim().isEmpty()) continue;
                                ps.setString(1, obj); ps.setString(2, framePkg); ps.setLong(3, ft);
                                ps.setString(4, x.text.trim()); ps.setLong(5, tid);
                                ps.setString(6, fshot); ps.setInt(7, lineNo++);
                                ps.setInt(8, x.top); ps.setString(9, "CONTENT");
                                if (fid > 0) ps.setLong(10, fid); else ps.setNull(10, java.sql.Types.INTEGER);
                                if (x.left > 0 || x.right > 0) { ps.setInt(11, x.left); ps.setInt(12, x.right); xOk++; }
                                else { ps.setNull(11, java.sql.Types.INTEGER); ps.setNull(12, java.sql.Types.INTEGER); }
                                ps.setLong(13, System.currentTimeMillis());
                                ps.executeUpdate(); totalLines++;
                                LineItem li = new LineItem();
                                li.objectKey = obj; li.pkg = framePkg; li.time = ft;
                                li.text = x.text; li.blockId = tid; li.frameId = fid;
                                li.shotRef = fshot;
                                li.yTop = x.top; li.left = x.left; li.right = x.right;
                                li.kind = "CONTENT"; allLines.add(li);
                            }
                        } else if (!ftext.isEmpty()) {
                            ps.setString(1, obj); ps.setString(2, framePkg); ps.setLong(3, ft);
                            ps.setString(4, ftext); ps.setLong(5, tid); ps.setString(6, null);
                            ps.setInt(7, 0); ps.setNull(8, java.sql.Types.INTEGER);
                            ps.setString(9, "CHROME"); ps.setLong(10, fid);
                            ps.setNull(11, java.sql.Types.INTEGER); ps.setNull(12, java.sql.Types.INTEGER);
                            ps.setLong(13, System.currentTimeMillis()); ps.executeUpdate(); totalLines++;
                        }
                    }
                }
            }
            Map<String, List<MessageBuilder.Anchor>> anchors = new HashMap<>();
            for (Map.Entry<String, List<Elem>> en : elems.entrySet()) {
                for (Elem x : en.getValue()) {
                    if (!"A11Y".equals(x.source) && !"BOTH".equals(x.source)) continue;
                    if (x.text == null || x.text.length() > 30 || "BUTTON".equals(x.role)) continue;
                    MessageBuilder.Anchor a = new MessageBuilder.Anchor();
                    a.text = x.text; a.left = x.left; a.top = x.top;
                    a.right = x.right; a.bottom = x.bottom;
                    anchors.computeIfAbsent(en.getKey(), k -> new ArrayList<>()).add(a);
                }
            }
            List<MessageBuilder.Msg> msgs = MessageBuilder.build(allLines, anchors);
            int self = 0, other = 0, unknown = 0, sent = 0, badPkg = 0, zeroMsgs = 0;
            try (PreparedStatement ps = m.prepareStatement("INSERT OR IGNORE INTO object_messages("
                    + "object_key,pkg,t_first,t_last,seen_count,sender,side,text,text_key,block_id,frame_id)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                for (MessageBuilder.Msg x : msgs) {
                    ps.setString(1, x.objectKey); ps.setString(2, x.pkg);
                    ps.setLong(3, x.tFirst); ps.setLong(4, x.tLast); ps.setInt(5, x.seenCount);
                    ps.setString(6, x.sender); ps.setString(7, x.side); ps.setString(8, x.text);
                    ps.setString(9, x.textKey); ps.setLong(10, x.blockId); ps.setLong(11, x.frameId);
                    ps.executeUpdate();
                    if (x.sender != null && !x.sender.isEmpty()) sent++;
                    if ("SELF".equals(x.side)) self++; else if ("OTHER".equals(x.side)) other++; else unknown++;
                    if (x.objectKey != null && x.objectKey.contains("零杯")
                            && !"com.tencent.mobileqq".equals(x.pkg)) badPkg++;
                    if (x.objectKey != null && x.objectKey.contains("零杯")) zeroMsgs++;
                }
            }
            System.out.println("selected traces=" + traces.size() + " shots=" + shots.size()
                    + " elements=" + countElems(elems) + " ms=" + (System.currentTimeMillis() - t0));
            System.out.println("lines=" + totalLines + "  x非空=" + xOk + "("
                    + pct(xOk, totalLines) + ")  frame object_key 覆盖=" + frameOk + "/" + frameTotal
                    + "(" + pct(frameOk, frameTotal) + ")");
            System.out.println("object_messages=" + msgs.size() + "  sender非空=" + sent
                    + "  side: SELF=" + self + "/OTHER=" + other + "/UNKNOWN=" + unknown);
            System.out.println("零杯: 消息=" + zeroMsgs + " 串台=" + badPkg + "(pkg不一致)");
        }
    }

    private static List<Object[]> loadTraces(Connection e, int lastN, long since, String object)
            throws Exception {
        StringBuilder sql = new StringBuilder("SELECT id,t0,pkg,page_title,object_key,shot_ref "
                + "FROM traces WHERE shot_ref IS NOT NULL AND shot_ref!=''");
        List<Object> ps = new ArrayList<>();
        if (since > 0) { sql.append(" AND t0>=?"); ps.add(since); }
        if (object != null && !object.isEmpty()) {
            sql.append(" AND (object_key LIKE ? OR page_title LIKE ?)");
            ps.add("%" + object + "%"); ps.add("%" + object + "%");
        }
        sql.append(" ORDER BY id DESC");
        if (lastN > 0) { sql.append(" LIMIT ?"); ps.add(lastN); }
        List<Object[]> out = new ArrayList<>();
        try (PreparedStatement st = e.prepareStatement(sql.toString())) {
            for (int i = 0; i < ps.size(); i++) st.setObject(i + 1, ps.get(i));
            try (ResultSet r = st.executeQuery()) {
                while (r.next()) out.add(new Object[]{r.getLong(1), r.getLong(2), r.getString(3),
                        r.getString(4), r.getString(5), r.getString(6)});
            }
        }
        return out;
    }

    private static Map<Long, List<Object[]>> loadFrames(Connection e, List<Object[]> traces)
            throws Exception {
        Map<Long, List<Object[]>> out = new HashMap<>();
        if (traces.isEmpty()) return out;
        for (int off = 0; off < traces.size(); off += 200) {
            int end = Math.min(traces.size(), off + 200);
            StringBuilder in = new StringBuilder();
            for (int i = off; i < end; i++) in.append(i == off ? "?" : ",?");
            try (PreparedStatement ps = e.prepareStatement("SELECT id,trace_id,seq,t_ms,"
                    + "shot_ref,text FROM trace_frames WHERE trace_id IN (" + in + ")")) {
                for (int i = off; i < end; i++) ps.setLong(i - off + 1, (Long) traces.get(i)[0]);
                try (ResultSet r = ps.executeQuery()) {
                    while (r.next()) out.computeIfAbsent(r.getLong(2), k -> new ArrayList<>())
                            .add(new Object[]{r.getLong(1), r.getLong(3), r.getLong(4),
                                    r.getString(5), r.getString(6)});
                }
            }
        }
        return out;
    }

    private static Map<String, List<Elem>> loadElements(Connection e, Set<String> shots)
            throws Exception {
        Map<String, List<Elem>> out = new HashMap<>();
        List<String> ss = new ArrayList<>(shots);
        for (int off = 0; off < ss.size(); off += 200) {
            int end = Math.min(ss.size(), off + 200);
            StringBuilder in = new StringBuilder();
            for (int i = off; i < end; i++) in.append(i == off ? "?" : ",?");
            try (PreparedStatement ps = e.prepareStatement("SELECT shot_ref,text,left,top,"
                    + "right,bottom,source,role,pkg FROM page_elements WHERE shot_ref IN ("
                    + in + ") AND text!=''")) {
                for (int i = off; i < end; i++) ps.setString(i - off + 1, ss.get(i));
                try (ResultSet r = ps.executeQuery()) {
                    while (r.next()) {
                        Elem x = new Elem(); String shot = r.getString(1);
                        x.text = r.getString(2); x.left = r.getInt(3); x.top = r.getInt(4);
                        x.right = r.getInt(5); x.bottom = r.getInt(6);
                        x.source = r.getString(7); x.role = r.getString(8); x.pkg = r.getString(9);
                        out.computeIfAbsent(shot, k -> new ArrayList<>()).add(x);
                    }
                }
            }
        }
        return out;
    }

    private static void init(Connection m) throws Exception {
        try (Statement s = m.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS lines(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "object_key TEXT,pkg TEXT,time INTEGER,text TEXT,block_id INTEGER,shot_ref TEXT,"
                    + "line_no INTEGER,y_top INTEGER,kind TEXT,frame_id INTEGER,x_left INTEGER,"
                    + "x_right INTEGER,created_at INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS object_messages(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "object_key TEXT,pkg TEXT,t_first INTEGER,t_last INTEGER,seen_count INTEGER,"
                    + "sender TEXT DEFAULT '',side TEXT DEFAULT 'UNKNOWN',text TEXT,text_key TEXT,"
                    + "block_id INTEGER,frame_id INTEGER)");
            s.execute("DELETE FROM lines"); s.execute("DELETE FROM object_messages");
        }
    }

    private static int countElems(Map<String, List<Elem>> m) {
        int n = 0; for (List<Elem> xs : m.values()) n += xs.size(); return n;
    }
    private static String arg(String[] a, String key, String def) {
        for (int i = 0; i + 1 < a.length; i++) if (key.equals(a[i])) return a[i + 1];
        return def;
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String pct(long a, long b) { return b <= 0 ? "0%" : String.format(java.util.Locale.ROOT, "%.1f%%", a * 100.0 / b); }
}
