package com.dsh.memory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** memory_status 的可读文本；计数纯逻辑，缺口由调用方传入。 */
public final class MemoryStatus {

    private MemoryStatus() { }

    public static String format(List<MemItem> all, int todayNew, int gapsClosed,
                                int gapsAppOnly, int gapsUnknown, String recent) {
        int total = all == null ? 0 : all.size();
        int trace = 0, chat = 0, manual = 0, present = 0;
        for (MemItem m : all) {
            if (m == null) continue;
            if (MemoryPolicy.NS_MANUAL.equals(m.namespace)) manual++;
            else if ("chat".equals(m.namespace)) chat++;
            else trace++;
            if (MemoryPolicy.present(m)) present++;
        }
        return "记忆：共 " + total + " 块（今天 +" + todayNew + "）\n"
                + "  按来源：trace " + trace + " / chat " + chat + " / manual " + manual + "\n"
                + "  在场（你当时在）：" + present + "   不在场（翻记录）：" + (total - present) + "\n"
                + "  最近写入：" + (recent == null ? "" : recent) + "\n"
                + "  缺口：今天 " + (gapsClosed + gapsAppOnly + gapsUnknown) + " 段（"
                + gapsClosed + " 段记录被关 / " + gapsAppOnly + " 段只有应用记录 / "
                + gapsUnknown + " 段不知道原因）";
    }

    public static String recentLine(MemItem m) {
        if (m == null) return "";
        String hm = Instant.ofEpochMilli(m.t0).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String who = MemoryPolicy.NS_MANUAL.equals(m.namespace) ? "手动"
                : "chat".equals(m.namespace) ? "聊天" : "记录";
        String txt = m.text == null ? "" : m.text.replace('\n', ' ');
        if (txt.length() > 20) txt = txt.substring(0, 20) + "…";
        return hm + " " + who + "「" + txt + "」(" + m.namespace + ")";
    }
}
