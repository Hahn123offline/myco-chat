package com.dsh.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跨窗口合并的候选生成：便宜规则先筛，模型只判候选对。
 * 纯逻辑，不依赖 Android / DB。
 */
public final class MergeCandidates {

    public static final long MAX_GAP_MS = 24 * 3600_000L;
    private static final Pattern ERR = Pattern.compile("\\b(?:ERR|E)[A-Z0-9_]{2,}\\b|\\b[A-Z0-9]+_[A-Z0-9_]+\\b");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z][A-Za-z0-9_+-]{2,}");
    private static final Pattern HAN2 = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}");
    private static final Pattern EXPLICIT = Pattern.compile(
            "刚才说的|刚才说|上一条|按上面|你说的那个|前面那条|刚才那");
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(
            "今天", "我们", "这个", "那个", "什么", "怎么", "可以", "不是", "就是",
            "一个", "然后", "但是", "因为", "所以", "时候", "没有", "看到", "进行",
            "查看", "打开", "使用", "问题", "相关", "内容", "记录", "一下",
            "应用", "功能", "页面", "信息", "操作", "时间", "相关", "系统", "设置",
            "杂项"));
    private static final Set<String> GENERIC_TITLES = new HashSet<>(Arrays.asList(
            "杂项", "查看系统设置", "查看手机状态", "查看通知栏", "息屏显示"));

    public static final class Item {
        public final long id;
        public final String title;
        public final String body;
        public final long t0, t1;
        public final String pkg;
        public final int level;
        public final long parentId;
        public final String childIds;
        public Item(long id, String title, String body, long t0, long t1, String pkg, int level) {
            this(id, title, body, t0, t1, pkg, level, 0, "");
        }
        public Item(long id, String title, String body, long t0, long t1, String pkg, int level,
                    long parentId, String childIds) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.t0 = t0;
            this.t1 = t1;
            this.pkg = pkg == null ? "" : pkg;
            this.level = level;
            this.parentId = parentId;
            this.childIds = childIds == null ? "" : childIds;
        }
    }

    public static final class Candidate {
        public final long a, b;
        public final String why;
        public final int score;
        public Candidate(long a, long b, String why) {
            this(a, b, why, 0);
        }
        public Candidate(long a, long b, String why, int score) {
            this.a = Math.min(a, b);
            this.b = Math.max(a, b);
            this.why = why;
            this.score = score;
        }
    }

    private MergeCandidates() { }

    public static List<Candidate> generate(List<Item> items) {
        List<Candidate> out = new ArrayList<>();
        if (items == null) return out;
        int n = items.size();
        java.util.Map<Long, Set<String>> termsById = new java.util.HashMap<>();
        java.util.Map<String, Integer> df = new java.util.HashMap<>();
        for (Item it : items) {
            // 候选词只看 title：L2 body 是多应用宫格生成，body 词重叠是假相似
            Set<String> t = terms(it.title);
            termsById.put(it.id, t);
            for (String x : t) df.put(x, df.getOrDefault(x, 0) + 1);
        }
        java.util.Set<String> tooCommon = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, Integer> e : df.entrySet()) {
            if (e.getValue() > n * 0.3) tooCommon.add(e.getKey());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Item a = items.get(i), b = items.get(j);
                if (a.parentId == b.id || b.parentId == a.id) continue;
                if (a.parentId > 0 && a.parentId == b.parentId) continue;
                if (!sameDay(a.t0, b.t0)) continue;
                long gap = Math.abs(a.t0 - b.t0);
                if (gap > MAX_GAP_MS) continue;

                Set<String> sharedErr = errors(a.title + " " + a.body);
                sharedErr.retainAll(errors(b.title + " " + b.body));
                Set<String> ta = new java.util.HashSet<>(termsById.get(a.id));
                Set<String> tb = new java.util.HashSet<>(termsById.get(b.id));
                ta.removeAll(tooCommon);
                tb.removeAll(tooCommon);
                Set<String> interSet = new java.util.HashSet<>(ta);
                interSet.retainAll(tb);
                interSet.removeAll(GENERIC);
                Set<String> unionSet = new java.util.HashSet<>(ta);
                unionSet.addAll(tb);
                unionSet.removeAll(GENERIC);
                double denom = 0, numer = 0;
                for (String t : unionSet) denom += idf(t, n, df);
                for (String t : interSet) numer += idf(t, n, df);

                boolean genericTitle = GENERIC_TITLES.contains(a.title.trim())
                        || GENERIC_TITLES.contains(b.title.trim());
                if (genericTitle && sharedErr.isEmpty()) continue;
                if ((ta.size() < 2 || tb.size() < 2) && sharedErr.isEmpty()) continue;
                double wj = denom <= 0 ? 0 : numer / denom;
                if (a.title.trim().equals(b.title.trim())) wj = Math.min(wj, 0.5);

                boolean samePkgNear = !a.pkg.isEmpty() && a.pkg.equals(b.pkg)
                        && gap <= 2 * 3600_000L && interSet.size() >= 1;
                boolean explicit = EXPLICIT.matcher(b.title + " " + b.body).find()
                        || EXPLICIT.matcher(a.title + " " + a.body).find();
                boolean explicitUsable = explicit && (wj > 0 || samePkgNear);
                List<String> why = new ArrayList<>();
                int score = 0;
                if (!sharedErr.isEmpty()) {
                    why.add("error_code=" + first(sharedErr));
                    score += 10000;
                }
                if (wj >= 0.15) {
                    why.add("wj=" + String.format("%.2f", wj)
                            + " terms=" + first2(interSet));
                    score += (int) (wj * 1000);
                }
                if (samePkgNear && wj >= 0.08) { why.add("same_pkg_near"); score += 50; }
                if (explicitUsable) { why.add("explicit_ref"); score += 50; }
                if (why.isEmpty()) continue;
                out.add(new Candidate(a.id, b.id, String.join("+", why), score));
            }
        }
        return out;
    }

    private static double idf(String t, int n, java.util.Map<String, Integer> df) {
        int d = df.getOrDefault(t, 0);
        return Math.log(n / (1.0 + d));
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        Calendar cb = Calendar.getInstance();
        ca.setTimeInMillis(a);
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static Set<String> errors(String s) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = ERR.matcher(s == null ? "" : s);
        while (m.find()) {
            String code = m.group();
            // ERR_PROXY_CONNECTION_FAILED 与 PROXY_CONNECTION_FAILED 视为同一错误码
            if (code.startsWith("ERR_")) code = code.substring(4);
            if (code.startsWith("E_")) code = code.substring(2);
            out.add(code);
        }
        return out;
    }

    private static Set<String> terms(String s) {
        Set<String> out = new LinkedHashSet<>();
        String t = s == null ? "" : s;
        Matcher latin = LATIN.matcher(t);
        while (latin.find()) out.add(latin.group().toLowerCase());
        Matcher han = HAN2.matcher(t);
        while (han.find()) {
            String x = han.group();
            // 中文按 2 字滑窗，避免整句只算一个词
            for (int i = 0; i + 2 <= x.length(); i++) {
                String gram = x.substring(i, i + 2);
                if (!GENERIC.contains(gram)) out.add(gram);
            }
        }
        return out;
    }

    private static String first(Set<String> s) {
        for (String x : s) return x;
        return "";
    }

    private static String first2(Set<String> s) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String x : s) {
            if (b.length() > 0) b.append(',');
            b.append(x);
            if (++n >= 2) break;
        }
        return b.toString();
    }
}
