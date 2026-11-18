package com.dsh.pcharness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 确定性失真审计：body 引号/数字/时间回到原始块文本做子串比对。 */
public final class Fidelity {

    private static final Pattern QUOTED = Pattern.compile("[“\"「『]([^”\"」』\\n]{2,40})[”\"」』]");
    private static final Pattern NUM = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern TIME = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");

    private Fidelity() { }

    public static void run(String[] args) throws Exception {
        String mem = Main.workspacePath(Main.arg(args, "--mem", "raw/v2/device/host_memory.db"));
        String ev = Main.workspacePath(Main.arg(args, "--evidence", "raw/v2/device/evidence.db"));
        String date = Main.arg(args, "--date", "2026-11-17");
        String out = Main.workspacePath(Main.arg(args, "--out",
                "raw/v2/fidelity-report-" + date + ".md"));
        LocalDate d = LocalDate.parse(date);
        long from = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long to = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<String> lines = new ArrayList<>();
        int nBody = 0, nQ = 0, qFound = 0, qMissing = 0;
        int nNum = 0, numFound = 0, numNorm = 0, numMissing = 0, numTime = 0;
        List<String> missing = new ArrayList<>();
        try (Connection mc = DriverManager.getConnection("jdbc:sqlite:" + mem);
             Connection ec = DriverManager.getConnection("jdbc:sqlite:" + ev)) {
            try (PreparedStatement ps = mc.prepareStatement(
                    "SELECT id,title,body,block_ids FROM chains WHERE level=0 AND status='done' "
                            + "AND t0>=? AND t0<?")) {
                ps.setLong(1, from); ps.setLong(2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String body = rs.getString(3);
                        if (body == null || body.trim().isEmpty()) continue;
                        nBody++;
                        String original = blocks(ec, rs.getString(4));
                        String normOrig = normalize(original);
                        Matcher qm = QUOTED.matcher(body);
                        while (qm.find()) {
                            nQ++;
                            String q = normalize(qm.group(1));
                            if (!q.isEmpty() && normOrig.contains(q)) qFound++;
                            else {
                                qMissing++;
                                missing.add("#" + rs.getLong(1) + " 引号「" + qm.group(1)
                                        + "」 body=" + snippet(body, qm.start())
                                        + " 原文=" + snippet(original, 0));
                            }
                        }
                        Matcher nm = NUM.matcher(body);
                        while (nm.find()) {
                            String raw = nm.group();
                            if (raw.length() < 2) continue;
                            nNum++;
                            String base = raw.replace(",", "").replace(".", "");
                            if (isTimeDerived(body, nm.start(), nm.end())) { numTime++; continue; }
                            if (normOrig.contains(base)) numFound++;
                            else if (normOrig.contains(normalizeUnit(raw))) numNorm++;
                            else {
                                numMissing++;
                                missing.add("#" + rs.getLong(1) + " 数字「" + raw
                                        + "」 body=" + snippet(body, nm.start())
                                        + " 原文=" + snippet(original, 0));
                            }
                        }
                    }
                }
            }
        }
        lines.add("# 信息失真审计 " + date);
        lines.add("");
        lines.add("确定性规则：body 引号内容 / 数字逐个回到原始块文本做子串比对；不调用模型。");
        lines.add("");
        lines.add("## 汇总");
        lines.add("");
        lines.add("- 有 body 的事件：" + nBody);
        lines.add("- 引号片段：" + nQ + "，原文可溯 " + qFound + "，找不到 " + qMissing);
        lines.add("- 数字：" + nNum + "，时间派生（不算缺失） " + numTime
                + "，原文可溯 " + numFound + "，归一化后命中 " + numNorm
                + "，**内容数字找不到 " + numMissing + "**");
        lines.add("");
        lines.add("## 找不到的片段（疑似编造 / OCR 对不上）");
        lines.add("");
        if (missing.isEmpty()) lines.add("无。");
        else for (String s : missing.subList(0, Math.min(80, missing.size()))) lines.add(s + "\n");
        Path p = Paths.get(out);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.write(p, lines, StandardCharsets.UTF_8);
        System.out.println("written " + out + " bodies=" + nBody + " quoted=" + nQ
                + " quotes_missing=" + qMissing + " numbers=" + nNum
                + " numbers_missing=" + numMissing);
    }

    private static String blocks(Connection ev, String csv) {
        StringBuilder b = new StringBuilder();
        if (csv == null) return "";
        try (PreparedStatement ps = ev.prepareStatement(
                "SELECT text FROM traces WHERE id=?")) {
            for (String s : csv.split(",")) {
                long id;
                try { id = Long.parseLong(s.trim()); } catch (Throwable ignored) { continue; }
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) if (rs.getString(1) != null) b.append(rs.getString(1)).append('\n');
                }
            }
        } catch (Throwable ignored) { }
        return b.toString();
    }

    private static boolean isTimeDerived(String body, int start, int end) {
        int a = Math.max(0, start - 4), b = Math.min(body.length(), end + 2);
        String w = body.substring(a, b);
        return w.contains(":");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").replace(",", "").replace("，", "")
                .replace("“", "").replace("”", "").replace("\"", "");
    }

    private static String normalizeUnit(String n) {
        return n.replace(",", "");
    }

    private static String snippet(String s, int at) {
        if (s == null) return "";
        int a = Math.max(0, at - 40), b = Math.min(s.length(), at + 80);
        return s.substring(a, b).replace('\n', ' ').replace("|", "\\|");
    }
}
